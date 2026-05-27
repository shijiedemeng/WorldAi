from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import json
import os
import shlex
import subprocess
import sys
from typing import Any
import uuid

from .api import AiApiClient
from .local_config import ControllableAgentConfig, LocalClientConfig
from .markdown_sync import sync_project_markdowns
from .mcp_file_service import handle_mcp_file_request


@dataclass
class CommandExecutionResult:
    status: str
    result_summary: str
    execution_details: str
    deliverable_path: str | None = None


@dataclass
class SessionCreationResult:
    status: str
    session_id: str | None = None
    runtime_type: str | None = None
    error_message: str | None = None
    initialized_skills: list[dict[str, Any]] | None = None


class ClientCommandRunner:
    def __init__(self, client: AiApiClient, config: LocalClientConfig) -> None:
        self.client = client
        self.config = config
        self.runtime_sessions: dict[str, dict[str, Any]] = {}
        self.acp_clients: dict[str, Any] = {}
        self.processing_session_requests: set[str] = set()
        self.completed_session_requests: set[str] = set()

    def run_pending_once(self) -> None:
        self.run_pending_session_creations_once()
        commands = self.client.list_pending_client_commands(self.config.client_code)
        for command in commands:
            self.run_command(command)

    def run_pending_session_creations_once(self) -> None:
        requests = self.client.list_pending_client_session_creations(self.config.client_code)
        for request in requests:
            self.run_session_create(request)

    def run_session_create(self, request: dict[str, Any]) -> None:
        request_id = str(request.get("requestId") or "")
        if not request_id:
            return
        if request_id in self.processing_session_requests or request_id in self.completed_session_requests:
            return
        self.processing_session_requests.add(request_id)
        try:
            try:
                result = self.create_agent_session(request)
            except Exception as exc:
                result = SessionCreationResult(
                    status="FAILED",
                    runtime_type=self.infer_request_runtime_type(request),
                    error_message=f"{type(exc).__name__}: {exc}",
                )
            self.client.complete_client_session_creation(
                self.config.client_code,
                request_id,
                {
                    "sessionId": result.session_id,
                    "status": result.status,
                    "runtimeType": result.runtime_type,
                    "errorMessage": result.error_message,
                    "initializedSkills": result.initialized_skills or [],
                },
            )
            self.completed_session_requests.add(request_id)
        finally:
            self.processing_session_requests.discard(request_id)

    def run_mcp_file_request(self, request: dict[str, Any]) -> None:
        request_id = str(request.get("requestId") or "")
        if not request_id:
            return
        response = handle_mcp_file_request(self.config, request)
        self.client.complete_mcp_file_request(request_id, response)

    def run_command(self, command: dict[str, Any]) -> None:
        command_id = str(command.get("commandId") or "")
        if not command_id:
            return
        self.client.start_client_command(self.config.client_code, command_id)
        try:
            result = self.execute(command)
        except Exception as exc:
            self.record_agent_session_event(
                command,
                "IN",
                "AGENT_ERROR",
                {
                    "commandId": command_id,
                    "requirementNo": command.get("requirementNo"),
                    "linkId": command.get("linkId"),
                    "errorType": type(exc).__name__,
                    "message": str(exc),
                },
            )
            result = CommandExecutionResult(
                status="FAILED",
                result_summary=f"{type(exc).__name__}: {exc}",
                execution_details=f"{type(exc).__name__}: {exc}",
            )
        self.client.complete_client_command(
            self.config.client_code,
            command_id,
            {
                "status": result.status,
                "resultSummary": result.result_summary,
                "executionDetails": result.execution_details,
                "deliverablePath": result.deliverable_path,
            },
        )

    def execute(self, command: dict[str, Any]) -> CommandExecutionResult:
        agent_code = str(command.get("agentCode") or "")
        agent_config = self.find_agent_config(agent_code)
        workspace_dir = Path(agent_config.workspace_dir).expanduser().resolve()
        if not workspace_dir.exists() or not workspace_dir.is_dir():
            raise ValueError(f"workspace_dir not found: {workspace_dir}")
        if not agent_config.worker_command:
            raise ValueError(f"worker_command not configured for agent {agent_code}")

        session_id = str(command.get("sessionId") or command.get("sessionCode") or "")
        initialized_skills = self.resolve_execution_initialized_skills(agent_config, workspace_dir, session_id or None)
        prompt = str(command.get("prompt") or "")
        self.record_agent_session_event(
            command,
            "OUT",
            "AGENT_PROMPT",
            {
                "commandId": command.get("commandId"),
                "requirementNo": command.get("requirementNo"),
                "linkId": command.get("linkId"),
                "agentCode": agent_code,
                "workspaceDir": str(workspace_dir),
                "prompt": prompt,
            },
        )
        if session_id and self.runtime_sessions.get(session_id, {}).get("sessionType") == "ACP":
            return self.run_acp_prompt(agent_config, session_id, prompt, workspace_dir, command)
        completed = self.run_cli_prompt(
            agent_config.worker_command,
            prompt,
            workspace_dir,
            command,
            initialized_skills,
        )
        stdout = (completed.stdout or "").strip()
        stderr = (completed.stderr or "").strip()
        parsed = parse_worker_payload(stdout)
        result_summary = str(parsed.get("resultSummary") or stdout or stderr or "客户端执行完成")[:4000]
        execution_details = str(parsed.get("executionDetails") or stdout or stderr or result_summary)[:20000]
        status = resolve_execution_status(parsed, completed.returncode, result_summary, execution_details, stderr)
        self.record_agent_session_event(
            command,
            "IN",
            "AGENT_RESPONSE",
            {
                "commandId": command.get("commandId"),
                "requirementNo": command.get("requirementNo"),
                "linkId": command.get("linkId"),
                "returnCode": completed.returncode,
                "stdout": stdout,
                "stderr": stderr,
                "status": status,
                "resultSummary": result_summary,
                "executionDetails": execution_details,
            },
        )
        return CommandExecutionResult(
            status=status,
            result_summary=result_summary,
            execution_details=execution_details,
            deliverable_path=none_if_blank(parsed.get("deliverablePath")),
        )

    def create_agent_session(self, request: dict[str, Any]) -> SessionCreationResult:
        agent_code = str(request.get("agentCode") or "")
        agent_config = self.find_agent_config(agent_code)
        workspace_dir = Path(str(request.get("workspaceDir") or agent_config.workspace_dir)).expanduser().resolve()
        if not workspace_dir.exists() or not workspace_dir.is_dir():
            raise ValueError(f"workspace_dir not found: {workspace_dir}")
        if not agent_config.worker_command:
            raise ValueError(f"worker_command not configured for agent {agent_code}")

        session_type = str(request.get("sessionType") or "CLI_PROMPT").upper()
        runtime_type = str(request.get("runtimeType") or infer_runtime_type(agent_config.worker_command))
        self.sync_agent_project_markdowns(agent_config, workspace_dir)
        initialized_skills = self.initialize_skills(agent_config, workspace_dir)
        development_context = self.prepare_development_context(agent_config, workspace_dir)
        if session_type == "ACP":
            session_id = self.create_acp_session(agent_config, workspace_dir)
        else:
            session_id = f"cli-{agent_code}-{uuid.uuid4().hex[:12]}"
        if development_context:
            initialized_skills = [*initialized_skills, self.summarize_development_context(development_context)]
        self.runtime_sessions[session_id] = {
            "agentCode": agent_code,
            "sessionType": session_type,
            "runtimeType": runtime_type,
            "workspaceDir": str(workspace_dir),
            "sessionName": request.get("sessionName") or "",
            "initializedSkills": initialized_skills,
            "developmentContext": development_context,
        }
        if development_context.get("loaded") and session_type == "ACP":
            self.inject_acp_development_context(agent_config, session_id, development_context)
        return SessionCreationResult(status="READY", session_id=session_id, runtime_type=runtime_type, initialized_skills=initialized_skills)

    def sync_agent_project_markdowns(self, agent_config: ControllableAgentConfig, workspace_dir: Path) -> None:
        try:
            agent = self.client.get_agent(agent_config.agent_code)
        except Exception:
            return
        project_code = str(agent.get("projectCode") or "").strip()
        if not project_code:
            return
        sync_project_markdowns(
            self.client,
            project_code,
            workspace_dir,
            client_code=self.config.client_code,
            agent_code=agent_config.agent_code,
        )

    def prepare_development_context(self, agent_config: ControllableAgentConfig, workspace_dir: Path) -> dict[str, Any]:
        try:
            agent = self.client.get_agent(agent_config.agent_code)
            project_code = str(agent.get("projectCode") or "").strip()
            agent_role = str(agent.get("agentRole") or "").strip()
            if not project_code:
                return {
                    "skillCode": "project-development-documents",
                    "skillName": "项目开发文档",
                    "matched": False,
                    "loaded": False,
                    "reason": "agent projectCode is empty",
                }
            usage_types = self.agent_document_usage_types(agent_role)
            context_parts: list[str] = []
            for usage_type, title in usage_types:
                content_part = self.client.get_project_document_context(project_code, usage_type).strip()
                if content_part:
                    context_parts.append(f"## {title}\n\n{content_part}")
            content = "\n\n".join(context_parts).strip()
            if not content:
                return {
                    "skillCode": "project-development-documents",
                    "skillName": "项目会话文档",
                    "projectCode": project_code,
                    "agentRole": agent_role or None,
                    "matched": True,
                    "loaded": False,
                    "reason": "session document context is empty",
                }
            return {
                "skillCode": "project-development-documents",
                "skillName": "项目会话文档",
                "projectCode": project_code,
                "agentRole": agent_role or None,
                "matched": True,
                "loaded": True,
                "contentLength": len(content),
                "usages": [usage_type for usage_type, _ in usage_types],
                "storage": "none",
                "content": content,
            }
        except Exception as exc:
            return {
                "skillCode": "project-development-documents",
                "skillName": "项目开发文档",
                "matched": False,
                "loaded": False,
                "error": f"{type(exc).__name__}: {exc}",
            }

    def agent_document_usage_types(self, agent_role: str) -> list[tuple[str, str]]:
        role_usage_map = {
            "MAIN": ("AGENT_MAIN", "主控文档"),
            "DEVELOPER": ("AGENT_DEVELOPER", "开发文档"),
            "TESTER": ("AGENT_TESTER", "测试文档"),
            "OPS": ("AGENT_OPS", "运维文档"),
            "REVIEWER": ("AGENT_REVIEWER", "评审文档"),
        }
        result = [("AGENT_COMMON", "通用文档")]
        role_key = (agent_role or "").strip().upper()
        role_usage = role_usage_map.get(role_key)
        if role_usage:
            result.append(role_usage)
        return result

    def inject_acp_development_context(
        self,
        agent_config: ControllableAgentConfig,
        session_id: str,
        development_context: dict[str, Any],
    ) -> None:
        content = str(development_context.get("content") or "").strip()
        if not content:
            return
        prompt = (
            "以下是当前项目配置的通用文档、开发文档和规范，作为本会话的长期上下文。"
            "后续执行任务时优先遵守这些规范，不要把这段初始化内容当成一个待执行任务。\n\n"
            f"{content[:30000]}"
        )
        client = self.acp_clients.get(agent_config.agent_code)
        if client is None:
            return
        try:
            client.prompt(session_id, prompt)
            development_context["injectedToAcp"] = True
        except Exception as exc:
            development_context["injectedToAcp"] = False
            development_context["injectError"] = f"{type(exc).__name__}: {exc}"

    def summarize_development_context(self, development_context: dict[str, Any]) -> dict[str, Any]:
        return {key: value for key, value in development_context.items() if key != "content"}

    def find_agent_config(self, agent_code: str) -> ControllableAgentConfig:
        for agent in self.config.agents:
            if agent.enabled and agent.agent_code == agent_code:
                return agent
        raise ValueError(f"agent not configured or disabled: {agent_code}")

    def infer_request_runtime_type(self, request: dict[str, Any]) -> str:
        agent_code = str(request.get("agentCode") or "")
        try:
            return infer_runtime_type(self.find_agent_config(agent_code).worker_command)
        except Exception:
            return infer_runtime_type(str(request.get("workerCommand") or ""))

    def initialize_skills(self, agent_config: ControllableAgentConfig, workspace_dir: Path) -> list[dict[str, Any]]:
        skill_root = Path(agent_config.skills_dir or workspace_dir / "skills").expanduser().resolve()
        if not skill_root.exists() or not skill_root.is_dir():
            return []

        server_skills = self.fetch_server_skills()
        selected_skill_codes = self.resolve_selected_skill_codes(agent_config.agent_code, server_skills)
        local_skill_map = self.discover_local_skills(skill_root)

        initialized: list[dict[str, Any]] = []
        for skill in server_skills:
            skill_code = str(skill.get("skillCode") or "").strip()
            if not skill_code:
                continue
            if skill.get("enabledFlag") is False:
                continue
            if selected_skill_codes and skill_code not in selected_skill_codes:
                continue
            local_skill = self.match_local_skill(skill, local_skill_map)
            if local_skill is None:
                initialized.append({
                    "skillCode": skill_code,
                    "skillName": skill.get("skillName"),
                    "localPath": None,
                    "matched": False,
                })
                continue
            self.run_skill_init_script(local_skill, workspace_dir)
            initialized.append({
                "skillCode": skill_code,
                "skillName": skill.get("skillName"),
                "localPath": str(local_skill),
                "matched": True,
            })
        return initialized

    def ensure_initialized_skills(
        self,
        agent_config: ControllableAgentConfig,
        workspace_dir: Path,
        session_id: str | None,
    ) -> list[dict[str, Any]]:
        if session_id:
            cached = self.runtime_sessions.get(session_id, {}).get("initializedSkills")
            if isinstance(cached, list):
                return cached
        initialized = self.initialize_skills(agent_config, workspace_dir)
        if session_id:
            self.runtime_sessions.setdefault(session_id, {})["initializedSkills"] = initialized
        return initialized

    def resolve_execution_initialized_skills(
        self,
        agent_config: ControllableAgentConfig,
        workspace_dir: Path,
        session_id: str | None,
    ) -> list[dict[str, Any]]:
        if session_id:
            cached = self.runtime_sessions.get(session_id, {}).get("initializedSkills")
            if isinstance(cached, list):
                return cached

        # Fallback for legacy/no-session commands. Normal long-session tasks are
        # prepared in create_agent_session and should not repeat initialization.
        self.sync_agent_project_markdowns(agent_config, workspace_dir)
        initialized = self.initialize_skills(agent_config, workspace_dir)
        if session_id:
            self.runtime_sessions.setdefault(session_id, {})["initializedSkills"] = initialized
        return initialized

    def fetch_server_skills(self) -> list[dict[str, Any]]:
        try:
            skills = self.client.list_skills()
        except Exception:
            return []
        return [skill for skill in skills if isinstance(skill, dict)]

    def resolve_selected_skill_codes(self, agent_code: str, server_skills: list[dict[str, Any]]) -> set[str]:
        try:
            agent = self.client.get_agent(agent_code)
        except Exception:
            agent = {}
        raw_skill_codes = agent.get("skillCodes")
        if isinstance(raw_skill_codes, list):
            selected = {str(item).strip() for item in raw_skill_codes if str(item).strip()}
        elif isinstance(raw_skill_codes, str):
            selected = {item.strip() for item in raw_skill_codes.split(",") if item.strip()}
        else:
            selected = set()
        if selected:
            return selected
        return {
            str(skill.get("skillCode") or "").strip()
            for skill in server_skills
            if isinstance(skill, dict) and str(skill.get("skillCode") or "").strip()
        }

    def discover_local_skills(self, skill_root: Path) -> dict[str, Path]:
        local_skill_map: dict[str, Path] = {}
        if not skill_root.exists():
            return local_skill_map
        for entry in skill_root.iterdir():
            if not entry.is_dir():
                continue
            if (entry / "SKILL.md").exists() or (entry / "skill-config.json").exists() or (entry / "skill-config.example.json").exists():
                local_skill_map[entry.name] = entry
        return local_skill_map

    def match_local_skill(self, skill: dict[str, Any], local_skill_map: dict[str, Path]) -> Path | None:
        candidates = [
            str(skill.get("skillCode") or "").strip(),
            str(skill.get("skillName") or "").strip(),
        ]
        for candidate in candidates:
            if candidate and candidate in local_skill_map:
                return local_skill_map[candidate]
        normalized_candidates = {
            candidate.replace(" ", "-").replace("_", "-").lower()
            for candidate in candidates
            if candidate
        }
        for key, value in local_skill_map.items():
            normalized_key = key.replace(" ", "-").replace("_", "-").lower()
            if normalized_key in normalized_candidates:
                return value
        return None

    def run_skill_init_script(self, skill_dir: Path, workspace_dir: Path) -> None:
        script_candidates = [
            skill_dir / "scripts" / "project_markdown_workspace.py",
            skill_dir / "scripts" / "ai_api_skill_client.py",
            skill_dir / "scripts" / "project_development_docs.py",
        ]
        for script in script_candidates:
            if not script.exists():
                continue
            command = [sys.executable, str(script)]
            if script.name == "project_markdown_workspace.py":
                command.extend(["load", "--workspace-dir", str(workspace_dir)])
            elif script.name == "project_development_docs.py":
                command.extend(["fetch", "--workspace-dir", str(workspace_dir)])
            else:
                command.extend(["config", "show", "--workspace-dir", str(workspace_dir)])
            completed = subprocess.run(command, capture_output=True, text=True, cwd=str(workspace_dir), check=False)
            if completed.returncode != 0:
                detail = (completed.stderr or completed.stdout or "").strip()
                raise ValueError(f"skill initialization failed: {script} {detail}")
            return
        if not (skill_dir / "SKILL.md").exists():
            raise ValueError(f"required skill definition not found: {skill_dir}")

    def create_acp_session(self, agent_config: ControllableAgentConfig, workspace_dir: Path) -> str:
        worker_command = str(agent_config.worker_command or "").strip()
        if not worker_command:
            raise ValueError("worker_command is empty")
        worker_command_name = Path(shlex.split(worker_command)[0]).name.lower()
        acp_command = acp_command_from_worker_command(worker_command)

        if "codex" in worker_command_name:
            from .cli_tools.acp import CodexAcpClient

            client = self.acp_clients.get(agent_config.agent_code)
            if not isinstance(client, CodexAcpClient) or is_dead_acp_client(client):
                client = CodexAcpClient(acp_command)
                client.initialize()
                self.acp_clients[agent_config.agent_code] = client
        elif "qoder" in worker_command_name:
            from .cli_tools.acp import QoderAcpClient

            client = self.acp_clients.get(agent_config.agent_code)
            if not isinstance(client, QoderAcpClient) or is_dead_acp_client(client):
                client = QoderAcpClient(acp_command)
                client.initialize()
                self.acp_clients[agent_config.agent_code] = client
        elif "claude" in worker_command_name:
            from .cli_tools.acp import ClaudeAcpClient

            client = self.acp_clients.get(agent_config.agent_code)
            if not isinstance(client, ClaudeAcpClient) or is_dead_acp_client(client):
                client = ClaudeAcpClient(acp_command)
                client.initialize()
                self.acp_clients[agent_config.agent_code] = client
        else:
            raise ValueError("ACP session currently supports qoder, codex or claude CLI only")
        return self.acp_clients[agent_config.agent_code].create_session(workspace_dir).session_id

    def run_acp_prompt(
        self,
        agent_config: ControllableAgentConfig,
        session_id: str,
        prompt: str,
        workspace_dir: Path,
        command: dict[str, Any],
    ) -> CommandExecutionResult:
        client = self.acp_clients.get(agent_config.agent_code)
        if client is None:
            raise ValueError(f"ACP client not initialized for agent {agent_config.agent_code}")
        result = client.prompt(session_id, prompt)
        text = (result.text or "").strip()
        thought = (result.thought or "").strip()
        summary = text or thought or "ACP 会话执行完成"
        self.record_agent_session_event(
            command,
            "IN",
            "AGENT_RESPONSE",
            {
                "commandId": command.get("commandId"),
                "requirementNo": command.get("requirementNo"),
                "linkId": command.get("linkId"),
                "text": text,
                "thought": thought,
                "resultSummary": summary,
            },
        )
        return CommandExecutionResult(
            status="SUCCESS",
            result_summary=summary[:4000],
            execution_details=(text or thought or summary)[:20000],
        )

    def record_agent_session_event(
        self,
        command: dict[str, Any],
        direction: str,
        event_type: str,
        payload: dict[str, Any],
    ) -> None:
        session_id = str(command.get("sessionId") or command.get("sessionCode") or "").strip()
        if not session_id:
            return
        try:
            self.client.append_client_session_event(
                self.config.client_code,
                session_id,
                {
                    "direction": direction,
                    "eventType": event_type,
                    "payload": json.dumps(payload, ensure_ascii=False, indent=2),
                },
            )
        except Exception:
            return

    def run_cli_prompt(
        self,
        worker_command: str,
        prompt: str,
        workspace_dir: Path,
        command: dict[str, Any],
        initialized_skills: list[dict[str, Any]] | None = None,
    ) -> subprocess.CompletedProcess[str]:
        env = os.environ.copy()
        env["AI_API_CLIENT_COMMAND_JSON"] = json.dumps(command, ensure_ascii=False)
        env["AI_API_CLIENT_COMMAND_ID"] = str(command.get("commandId") or "")
        env["AI_API_CLIENT_SESSION_ID"] = str(command.get("sessionId") or command.get("sessionCode") or "")
        env["AI_API_REQUIREMENT_NO"] = str(command.get("requirementNo") or "")
        env["AI_API_LINK_ID"] = str(command.get("linkId") or "")
        session_id = str(command.get("sessionId") or command.get("sessionCode") or "")
        if session_id:
            env["AI_API_CLIENT_SESSION_TYPE"] = str(self.runtime_sessions.get(session_id, {}).get("sessionType") or "")
        if initialized_skills is not None:
            env["AI_API_CLIENT_INITIALIZED_SKILLS_JSON"] = json.dumps(initialized_skills, ensure_ascii=False)

        if "{prompt}" in worker_command:
            shell_command = worker_command.replace("{prompt}", shlex.quote(prompt))
            return subprocess.run(shell_command, shell=True, capture_output=True, text=True, cwd=str(workspace_dir), env=env, timeout=None, check=False)

        argv = shlex.split(worker_command)
        if not argv:
            raise ValueError("worker_command is empty")
        return subprocess.run([*argv, "-p", prompt], capture_output=True, text=True, cwd=str(workspace_dir), env=env, timeout=None, check=False)


def parse_worker_payload(stdout: str) -> dict[str, Any]:
    if not stdout:
        return {}
    try:
        parsed = json.loads(stdout)
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def resolve_execution_status(
    parsed: dict[str, Any],
    return_code: int,
    result_summary: str,
    execution_details: str,
    stderr: str,
) -> str:
    raw_status = str(parsed.get("status") or "").strip().upper()
    if raw_status in {"SUCCESS", "FAILED", "BLOCKED", "CANCELLED", "TIMEOUT"}:
        return raw_status
    if return_code != 0:
        return "FAILED"
    merged = "\n".join([result_summary, execution_details, stderr]).lower()
    blocked_markers = [
        "blocked",
        "cannot complete",
        "can't complete",
        "unable to execute",
        "unable to complete",
        "permission approval",
        "permission system is blocking",
        "无法完成",
        "权限",
    ]
    if any(marker in merged for marker in blocked_markers):
        return "BLOCKED"
    return "SUCCESS"


def none_if_blank(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def infer_runtime_type(worker_command: str) -> str:
    lowered = worker_command.lower()
    if "qoder" in lowered:
        return "QODER_CLI"
    if "claude" in lowered:
        return "CLAUDE_CODE"
    return "CODEX_CLI"


def acp_command_from_worker_command(worker_command: str) -> str:
    argv = shlex.split(worker_command)
    if not argv:
        raise ValueError("worker_command is empty")
    worker_command_name = Path(argv[0]).name.lower()
    if "codex" in worker_command_name:
        if "app-server" not in argv:
            argv.append("app-server")
        if "--listen" not in argv:
            argv.extend(["--listen", "stdio://"])
    elif "qoder" in worker_command_name:
        if "--acp" not in argv:
            argv.append("--acp")
    elif "claude" in worker_command_name:
        if "--acp" not in argv:
            argv.append("--acp")
    else:
        raise ValueError("ACP session currently supports qoder, codex or claude CLI only")
    return " ".join(shlex.quote(item) for item in argv)


def is_dead_acp_client(client: Any) -> bool:
    proc = getattr(client, "proc", None)
    poll = getattr(proc, "poll", None)
    if not callable(poll):
        return False
    return poll() is not None
