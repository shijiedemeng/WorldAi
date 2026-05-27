from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from typing import Any

from .api import AiApiClient
from .markdown_sync import MarkdownSyncConflict, sync_project_markdowns


@dataclass
class SchedulerConfig:
    base_url: str
    agent_code: str
    agent_name: str
    agent_role: str
    agent_desc: str = ""
    supported_link_types: str = ""
    capability_tags: str = ""
    callback_mode: str = "PULL"
    endpoint_url: str = ""
    status: str = "ONLINE"
    project_code: str | None = None
    workspace_dir: Path | None = None
    sync_markdowns: bool = False
    sync_only: bool = False
    poll_interval: float = 15.0
    heartbeat_interval: float = 30.0
    worker_command: str | None = None
    worker_timeout: float | None = None
    once: bool = False


@dataclass
class WorkerExecutionResult:
    return_code: int
    result_summary: str
    execution_details: str | None = None
    deliverable_path: str | None = None
    stdout: str = ""
    stderr: str = ""


class AgentScheduler:
    def __init__(self, config: SchedulerConfig, client: AiApiClient | None = None) -> None:
        self.config = config
        self.client = client or AiApiClient(config.base_url)
        self._next_heartbeat_at = 0.0
        self._requirement_project_cache: dict[str, str] = {}

    def run(self) -> None:
        self.ensure_agent()
        if self.config.sync_markdowns and self.config.project_code:
            self.sync_project_markdowns(self.config.project_code)
        if self.config.sync_only:
            return
        if self.config.once:
            self.run_cycle()
            return

        while True:
            self.run_cycle()
            time.sleep(max(0.1, self.config.poll_interval))

    def run_cycle(self) -> None:
        self.send_heartbeat_if_due()
        tasks = self.client.list_tasks(self.config.agent_code)
        todo_tasks = [task for task in tasks if task.get("status") == "TODO"]
        if not todo_tasks:
            self._log("no TODO tasks")
            return
        for task in todo_tasks:
            self.process_task(task)

    def ensure_agent(self) -> None:
        payload = {
            "agentCode": self.config.agent_code,
            "agentName": self.config.agent_name,
            "agentRole": self.config.agent_role,
            "agentDesc": self.config.agent_desc,
            "capabilityTags": self.config.capability_tags,
            "supportedLinkTypes": self.config.supported_link_types,
            "callbackMode": self.config.callback_mode,
            "endpointUrl": self.config.endpoint_url,
            "status": self.config.status,
        }
        agent = self.client.ensure_agent(payload)
        self._log(f"agent ready: {agent.get('agentCode')}")

    def send_heartbeat_if_due(self) -> None:
        now = time.monotonic()
        if now < self._next_heartbeat_at:
            return
        self.client.heartbeat(self.config.agent_code)
        self._next_heartbeat_at = now + max(1.0, self.config.heartbeat_interval)
        self._log("heartbeat sent")

    def process_task(self, task: dict[str, Any]) -> None:
        task_id = task.get("id")
        task_title = task.get("taskTitle") or task_id
        try:
            if self.config.sync_markdowns:
                project_code = self.resolve_project_code(task)
                self.sync_project_markdowns(project_code)

            if not self.config.worker_command:
                self._log(f"task {task_id} pending but no worker command configured")
                return

            started_at = task.get("startedAt") or self._timestamp()
            self.safe_update_progress(task, status="DOING", started_at=started_at, finished_at=None)
            result = self.run_worker(task)
            finished_at = self._timestamp()

            if result.return_code == 0:
                self.safe_update_progress(
                    task,
                    status="DONE",
                    result_summary=result.result_summary,
                    execution_details=result.execution_details,
                    deliverable_path=result.deliverable_path,
                    started_at=started_at,
                    finished_at=finished_at,
                )
                self._log(f"task {task_id} done: {task_title}")
                return

            self.safe_update_progress(
                task,
                status="BLOCKED",
                result_summary=result.result_summary,
                execution_details=result.execution_details,
                deliverable_path=result.deliverable_path,
                started_at=started_at,
                finished_at=finished_at,
            )
            self._log(f"task {task_id} blocked: {task_title}")
        except MarkdownSyncConflict as exc:
            summary = f"Markdown sync conflict: {', '.join(exc.report.conflict_files)}"
            self.safe_update_progress(
                task,
                status="BLOCKED",
                result_summary=summary,
                execution_details=summary,
                started_at=task.get("startedAt") or self._timestamp(),
                finished_at=self._timestamp(),
            )
            self._log(f"task {task_id} blocked before execution: {summary}")
        except Exception as exc:
            summary = f"{type(exc).__name__}: {exc}"
            self.safe_update_progress(
                task,
                status="BLOCKED",
                result_summary=summary,
                execution_details=summary,
                started_at=task.get("startedAt") or self._timestamp(),
                finished_at=self._timestamp(),
            )
            self._log(f"task {task_id} failed: {summary}")

    def resolve_project_code(self, task: dict[str, Any]) -> str:
        if self.config.project_code:
            return self.config.project_code
        requirement_no = str(task.get("requirementNo") or "")
        if not requirement_no:
            raise ValueError("task is missing requirementNo")
        cached = self._requirement_project_cache.get(requirement_no)
        if cached:
            return cached
        requirement = self.client.get_requirement(requirement_no)
        project_code = str(requirement.get("projectCode") or "")
        if not project_code:
            raise ValueError(f"projectCode not found for requirement {requirement_no}")
        self._requirement_project_cache[requirement_no] = project_code
        return project_code

    def sync_project_markdowns(self, project_code: str) -> None:
        if not self.config.workspace_dir:
            raise ValueError("workspace_dir is required when markdown sync is enabled")
        report = sync_project_markdowns(
            self.client,
            project_code,
            self.config.workspace_dir,
            agent_code=self.config.agent_code,
        )
        self._log(
            f"markdown sync for {project_code}: "
            f"written={len(report.written_files)} uploaded={len(report.uploaded_files)} skipped={len(report.skipped_files)}"
        )

    def safe_update_progress(
        self,
        task: dict[str, Any],
        *,
        status: str,
        result_summary: str | None = None,
        execution_details: str | None = None,
        deliverable_path: str | None = None,
        started_at: str | None = None,
        finished_at: str | None = None,
    ) -> None:
        payload = {
            "status": status,
            "resultSummary": task.get("resultSummary") if result_summary is None else result_summary,
            "executionDetails": task.get("executionDetails") if execution_details is None else execution_details,
            "deliverablePath": task.get("deliverablePath") if deliverable_path is None else deliverable_path,
            "startedAt": task.get("startedAt") if started_at is None else started_at,
            "finishedAt": task.get("finishedAt") if finished_at is None else finished_at,
        }
        updated = self.client.update_link_progress(task["id"], payload)
        task.update(updated)

    def run_worker(self, task: dict[str, Any]) -> WorkerExecutionResult:
        assert self.config.worker_command
        project_code = self.config.project_code or self.resolve_project_code(task)
        payload_json = json.dumps(task, ensure_ascii=False)
        env = os.environ.copy()
        env["AI_API_TASK_JSON"] = payload_json
        env["AI_API_TASK_ID"] = str(task.get("id") or "")
        env["AI_API_REQUIREMENT_NO"] = str(task.get("requirementNo") or "")
        env["AI_API_LINK_TYPE"] = str(task.get("linkType") or "")
        env["AI_API_PROJECT_CODE"] = project_code

        completed = subprocess.run(
            self.config.worker_command,
            input=payload_json,
            capture_output=True,
            text=True,
            shell=True,
            cwd=str(self.config.workspace_dir) if self.config.workspace_dir else None,
            env=env,
            timeout=self.config.worker_timeout,
            check=False,
        )
        stdout = (completed.stdout or "").strip()
        stderr = (completed.stderr or "").strip()
        parsed = _parse_worker_payload(stdout)
        summary = parsed.get("resultSummary") or stdout or stderr
        if not summary:
            summary = f"worker exited with code {completed.returncode}"
        execution_details = parsed.get("executionDetails") or stdout or stderr or None
        return WorkerExecutionResult(
            return_code=completed.returncode,
            result_summary=str(summary)[:4000],
            execution_details=str(execution_details)[:20000] if execution_details else None,
            deliverable_path=_none_if_blank(parsed.get("deliverablePath")),
            stdout=stdout,
            stderr=stderr,
        )

    @staticmethod
    def _timestamp() -> str:
        return datetime.now().isoformat(timespec="seconds")

    @staticmethod
    def _log(message: str) -> None:
        print(message, file=sys.stderr, flush=True)


def _parse_worker_payload(stdout: str) -> dict[str, Any]:
    if not stdout:
        return {}
    try:
        parsed = json.loads(stdout)
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


def _none_if_blank(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None
