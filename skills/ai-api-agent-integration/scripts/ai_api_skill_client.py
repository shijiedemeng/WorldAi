#!/usr/bin/env python3
from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import date, datetime
import json
import os
from pathlib import Path
import sys
from typing import Any, Mapping
from urllib import error, parse, request

SKILL_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = SKILL_ROOT / "skill-config.json"
_ACTIVE_WORKSPACE_DIR: Path | None = None
GLOBAL_OPTIONS_WITH_VALUES = {"--base-url", "--workspace-dir", "--timeout"}


def _to_jsonable(value: Any) -> Any:
    if isinstance(value, datetime):
        return value.isoformat(timespec="seconds")
    if isinstance(value, date):
        return value.isoformat()
    if isinstance(value, Path):
        return str(value)
    if isinstance(value, Mapping):
        return {k: _to_jsonable(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [_to_jsonable(v) for v in value]
    return value


@dataclass
class ApiError(Exception):
    message: str
    http_status: int | None = None
    api_code: int | None = None
    body: str = ""

    def __str__(self) -> str:
        parts = [self.message]
        if self.http_status is not None:
            parts.append(f"http={self.http_status}")
        if self.api_code is not None:
            parts.append(f"code={self.api_code}")
        return " ".join(parts)


class AiApiSkillClient:
    def __init__(self, base_url: str, timeout: float = 10.0) -> None:
        normalized = base_url.rstrip("/")
        self.api_base = normalized if normalized.endswith("/api") else f"{normalized}/api"
        self.timeout = timeout

    def get(self, path: str, query: Mapping[str, Any] | None = None) -> Any:
        return self._request("GET", path, query=query)

    def post(self, path: str, payload: Mapping[str, Any] | None = None) -> Any:
        return self._request("POST", path, payload=payload)

    def put(self, path: str, payload: Mapping[str, Any] | None = None) -> Any:
        return self._request("PUT", path, payload=payload)

    def get_markdown_document(self, document_id: str) -> Any:
        return self.get(f"/markdown-documents/{parse.quote(document_id)}")

    def get_markdown_document_subtree(self, document_id: str) -> Any:
        return self.get(f"/markdown-documents/{parse.quote(document_id)}/subtree")

    def get_markdown_document_path(self, document_id: str) -> Any:
        return self.get(f"/markdown-documents/{parse.quote(document_id)}/path")

    def get_markdown_document_refs(self, document_id: str) -> Any:
        return self.get(f"/markdown-documents/{parse.quote(document_id)}/refs")

    def _request(
        self,
        method: str,
        path: str,
        payload: Mapping[str, Any] | None = None,
        query: Mapping[str, Any] | None = None,
    ) -> Any:
        url = f"{self.api_base}{path if path.startswith('/') else '/' + path}"
        if query:
            query_items = [(k, str(v)) for k, v in query.items() if v is not None]
            if query_items:
                url = f"{url}?{parse.urlencode(query_items)}"

        headers = {"Accept": "application/json"}
        body = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            body = json.dumps(_to_jsonable(payload), ensure_ascii=False).encode("utf-8")

        req = request.Request(url, data=body, headers=headers, method=method.upper())
        try:
            with request.urlopen(req, timeout=self.timeout) as resp:
                raw = resp.read().decode("utf-8")
        except error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            parsed = _parse_json(raw)
            if isinstance(parsed, dict):
                raise ApiError(
                    message=str(parsed.get("message", raw or "request failed")),
                    http_status=exc.code,
                    api_code=_to_int(parsed.get("code")),
                    body=raw,
                ) from exc
            raise ApiError(message=raw or str(exc), http_status=exc.code, body=raw) from exc
        except error.URLError as exc:
            raise ApiError(message=f"network error: {exc.reason}") from exc

        parsed = _parse_json(raw)
        if not isinstance(parsed, dict):
            raise ApiError(message="invalid JSON response", body=raw)
        code = _to_int(parsed.get("code"))
        if code != 0:
            raise ApiError(
                message=str(parsed.get("message", "application error")),
                api_code=code,
                body=raw,
            )
        return parsed.get("data")


def _parse_json(raw: str) -> Any:
    if not raw.strip():
        return {}
    return json.loads(raw)


def _to_int(value: Any) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def print_json(data: Any) -> None:
    print(json.dumps(data, ensure_ascii=False, indent=2, default=str))


def _resolve_workspace_dir(raw: str | None = None) -> Path:
    workspace = raw or os.getenv("AI_API_SKILL_WORKSPACE_DIR") or os.getcwd()
    return Path(workspace).expanduser().resolve()


def _set_active_workspace_dir(raw: str | None = None) -> Path:
    global _ACTIVE_WORKSPACE_DIR
    _ACTIVE_WORKSPACE_DIR = _resolve_workspace_dir(raw)
    return _ACTIVE_WORKSPACE_DIR


def _active_workspace_dir() -> Path:
    return _ACTIVE_WORKSPACE_DIR or _set_active_workspace_dir()


def _read_skill_config_file() -> dict[str, Any]:
    if not CONFIG_PATH.exists():
        return {}
    try:
        raw = CONFIG_PATH.read_text(encoding="utf-8")
        parsed = json.loads(raw)
    except OSError as exc:
        raise ValueError(f"failed to read skill config: {CONFIG_PATH} {exc}") from exc
    except json.JSONDecodeError as exc:
        raise ValueError(f"invalid skill config JSON: {CONFIG_PATH} {exc}") from exc
    return parsed if isinstance(parsed, dict) else {}


def _config_entries(raw_config: Mapping[str, Any]) -> list[dict[str, Any]]:
    workspaces = raw_config.get("workspaces")
    if isinstance(workspaces, list):
        return [dict(item) for item in workspaces if isinstance(item, dict)]
    if raw_config:
        return [dict(raw_config)]
    return []


def _entry_workspace_dir(entry: Mapping[str, Any]) -> Path | None:
    raw = str(entry.get("workspaceDir") or entry.get("workspace_dir") or "").strip()
    if not raw:
        return None
    return Path(raw).expanduser().resolve()


def _workspace_matches(workspace_dir: Path, configured_dir: Path) -> bool:
    return workspace_dir == configured_dir or configured_dir in workspace_dir.parents


def _select_workspace_config(raw_config: Mapping[str, Any], workspace_dir: Path, *, required: bool) -> dict[str, Any]:
    candidates: list[tuple[int, dict[str, Any]]] = []
    for entry in _config_entries(raw_config):
        configured_dir = _entry_workspace_dir(entry)
        if configured_dir and _workspace_matches(workspace_dir, configured_dir):
            normalized = dict(entry)
            normalized["workspaceDir"] = str(configured_dir)
            candidates.append((len(configured_dir.parts), normalized))
    if candidates:
        candidates.sort(key=lambda item: item[0], reverse=True)
        return candidates[0][1]
    if required:
        raise ValueError(
            f"当前工作目录没有匹配的 skill 配置：{workspace_dir}。"
            f"请在 {CONFIG_PATH} 的 workspaces 中新增 workspaceDir。"
        )
    return {}


def load_skill_config(*, required: bool = True) -> dict[str, Any]:
    raw_config = _read_skill_config_file()
    if not raw_config:
        if required:
            raise ValueError(f"skill config not found: {CONFIG_PATH}")
        return {}
    return _select_workspace_config(raw_config, _active_workspace_dir(), required=required)


def save_skill_config(config: Mapping[str, Any]) -> None:
    workspace_dir = _active_workspace_dir()
    raw_config = _read_skill_config_file()
    entries: list[dict[str, Any]] = []
    for entry in _config_entries(raw_config):
        configured_dir = _entry_workspace_dir(entry)
        if configured_dir and configured_dir == workspace_dir:
            continue
        if configured_dir:
            normalized = dict(entry)
            normalized["workspaceDir"] = str(configured_dir)
            entries.append(normalized)
    current = dict(config)
    current["workspaceDir"] = str(workspace_dir)
    entries.append(current)
    CONFIG_PATH.write_text(
        json.dumps({"workspaces": entries}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def clear_skill_config() -> bool:
    workspace_dir = _active_workspace_dir()
    raw_config = _read_skill_config_file()
    entries: list[dict[str, Any]] = []
    cleared = False
    for entry in _config_entries(raw_config):
        configured_dir = _entry_workspace_dir(entry)
        if configured_dir and configured_dir == workspace_dir:
            cleared = True
            continue
        if configured_dir:
            normalized = dict(entry)
            normalized["workspaceDir"] = str(configured_dir)
            entries.append(normalized)
    if not entries:
        if CONFIG_PATH.exists():
            CONFIG_PATH.unlink()
        return cleared
    CONFIG_PATH.write_text(
        json.dumps({"workspaces": entries}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return cleared


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="ai-api skill helper")
    parser.add_argument("--base-url")
    parser.add_argument("--workspace-dir")
    parser.add_argument("--timeout", type=float, default=10.0)
    subparsers = parser.add_subparsers(dest="resource", required=True)

    init = subparsers.add_parser("init", help="initialize project and agent context")
    _add_init_args(init)
    init.set_defaults(handler=_handle_init)

    config = subparsers.add_parser("config", help="skill config operations")
    config_sub = config.add_subparsers(dest="action", required=True)
    config_show = config_sub.add_parser("show", help="show saved defaults")
    config_show.set_defaults(handler=_handle_config_show)
    config_set_project = config_sub.add_parser("set-project", help="set default project code")
    config_set_project.add_argument("--project-code", required=True)
    config_set_project.set_defaults(handler=_handle_config_set_project)
    config_clear = config_sub.add_parser("clear", help="clear saved defaults")
    config_clear.set_defaults(handler=_handle_config_clear)

    projects = subparsers.add_parser("projects", help="project operations")
    projects_sub = projects.add_subparsers(dest="action", required=True)
    projects_list = projects_sub.add_parser("list", help="list projects")
    projects_list.set_defaults(handler=_handle_projects_list)
    projects_get = projects_sub.add_parser("get", help="get project by code")
    projects_get.add_argument("--project-code")
    projects_get.set_defaults(handler=_handle_projects_get)
    projects_markdown = projects_sub.add_parser("markdown", help="get/update markdown config")
    projects_markdown_sub = projects_markdown.add_subparsers(dest="action2", required=True)
    projects_markdown_get = projects_markdown_sub.add_parser("get", help="get markdown config")
    projects_markdown_get.add_argument("--project-code")
    projects_markdown_get.set_defaults(handler=_handle_projects_markdown_get)

    documents = subparsers.add_parser("documents", help="markdown document operations")
    documents_sub = documents.add_subparsers(dest="action", required=True)
    documents_get = documents_sub.add_parser("get", help="get markdown document by documentId")
    documents_get.add_argument("--document-id", required=True)
    documents_get.set_defaults(handler=_handle_documents_get)
    documents_subtree = documents_sub.add_parser("subtree", help="get markdown document subtree by documentId")
    documents_subtree.add_argument("--document-id", required=True)
    documents_subtree.set_defaults(handler=_handle_documents_subtree)
    documents_path = documents_sub.add_parser("path", help="get markdown document path by documentId")
    documents_path.add_argument("--document-id", required=True)
    documents_path.set_defaults(handler=_handle_documents_path)
    documents_refs = documents_sub.add_parser("refs", help="get markdown document refs by documentId")
    documents_refs.add_argument("--document-id", required=True)
    documents_refs.set_defaults(handler=_handle_documents_refs)

    agents = subparsers.add_parser("agents", help="agent operations")
    agents_sub = agents.add_subparsers(dest="action", required=True)
    agents_get = agents_sub.add_parser("get", help="get agent by code")
    agents_get.add_argument("--agent-code")
    agents_get.set_defaults(handler=_handle_agents_get)
    agents_list = agents_sub.add_parser("list", help="list agents under project")
    agents_list.add_argument("--project-code")
    agents_list.set_defaults(handler=_handle_agents_list)
    agents_ensure = agents_sub.add_parser("ensure", help="ensure agent exists")
    _add_agent_create_args(agents_ensure)
    agents_ensure.add_argument("--project-code")
    agents_ensure.set_defaults(handler=_handle_agents_ensure)
    agents_next = agents_sub.add_parser("next", help="show ready tasks for current agent in execution order")
    agents_next.add_argument("--agent-code")
    agents_next.add_argument("--claim", action="store_true", help="mark current non-ACP task as IN_PROGRESS/DOING before execution")
    agents_next.set_defaults(handler=_handle_agents_next)
    agents_tasks = agents_sub.add_parser("tasks", help="list current tasks for agent")
    agents_tasks.add_argument("--agent-code")
    agents_tasks.set_defaults(handler=_handle_agents_tasks)

    requirements = subparsers.add_parser("requirements", help="requirement operations")
    req_sub = requirements.add_subparsers(dest="action", required=True)
    req_get = req_sub.add_parser("get", help="get requirement")
    req_get.add_argument("--requirement-no", required=True)
    req_get.set_defaults(handler=_handle_requirements_get)
    req_list = req_sub.add_parser("list", help="list requirements")
    req_list.add_argument("--project-code")
    req_list.add_argument("--open-only", action="store_true", help="only list non-completed requirements")
    req_list.add_argument("--agent-code", help="filter current agent requirements")
    req_list.set_defaults(handler=_handle_requirements_list)
    req_workflow_results = req_sub.add_parser("workflow-results", help="get extractable finished workflow results")
    req_workflow_results.add_argument("--requirement-no", required=True)
    req_workflow_results.set_defaults(handler=_handle_requirements_workflow_results)
    req_inspection = req_sub.add_parser("inspection", help="inspect requirement")
    req_inspection.add_argument("--requirement-no", required=True)
    req_inspection.set_defaults(handler=_handle_requirements_inspection)
    req_list_links = req_sub.add_parser("links", help="list links for requirement")
    req_list_links.add_argument("--requirement-no", required=True)
    req_list_links.set_defaults(handler=_handle_requirements_links)
    req_create = req_sub.add_parser("create", help="create requirement")
    _add_requirement_create_args(req_create)
    req_create.set_defaults(handler=_handle_requirements_create)
    req_child = req_sub.add_parser("child", help="create child requirement")
    req_child.add_argument("--master-requirement-no", required=True)
    _add_requirement_create_args(req_child)
    req_child.set_defaults(handler=_handle_requirements_child)
    req_children = req_sub.add_parser("children", help="list child requirements under master requirement")
    req_children.add_argument("--master-requirement-no", required=True)
    req_children.set_defaults(handler=_handle_requirements_children)
    req_update = req_sub.add_parser("update", help="update requirement")
    req_update.add_argument("--requirement-no", required=True)
    _add_requirement_update_args(req_update)
    req_update.set_defaults(handler=_handle_requirements_update)
    req_delete = req_sub.add_parser("delete", help="delete requirement")
    req_delete.add_argument("--requirement-no", required=True)
    req_delete.set_defaults(handler=_handle_requirements_delete)
    req_status = req_sub.add_parser("status", help="update requirement status")
    req_status.add_argument("--requirement-no", required=True)
    req_status.add_argument("--status", required=True)
    req_status.set_defaults(handler=_handle_requirements_status)
    req_pref = req_sub.add_parser("session-preference", help="update session preference")
    req_pref.add_argument("--requirement-no", required=True)
    req_pref.add_argument("--session-strategy")
    req_pref.add_argument("--preferred-session-code")
    req_pref.set_defaults(handler=_handle_requirements_session_preference)
    req_link = req_sub.add_parser("link", help="create link under requirement")
    req_link.add_argument("--requirement-no", required=True)
    _add_link_create_args(req_link)
    req_link.set_defaults(handler=_handle_requirements_link_create)

    links = subparsers.add_parser("links", help="link operations")
    links_sub = links.add_subparsers(dest="action", required=True)
    link_update = links_sub.add_parser("update", help="update link progress")
    link_update.add_argument("--link-id", required=True)
    _add_link_update_args(link_update)
    link_update.set_defaults(handler=_handle_links_update)

    sessions = subparsers.add_parser("sessions", help="session operations")
    sess_sub = sessions.add_subparsers(dest="action", required=True)
    sess_create = sess_sub.add_parser("create", help="create session")
    _add_session_create_args(sess_create)
    sess_create.set_defaults(handler=_handle_sessions_create)
    sess_list = sess_sub.add_parser("list", help="list sessions")
    sess_list.add_argument("--project-code")
    sess_list.add_argument("--agent-code")
    sess_list.add_argument("--client-code")
    sess_list.add_argument("--root-requirement-no")
    sess_list.add_argument("--reusable-flag")
    sess_list.add_argument("--status")
    sess_list.set_defaults(handler=_handle_sessions_list)
    sess_get = sess_sub.add_parser("get", help="get session")
    sess_get.add_argument("--session-code", required=True)
    sess_get.set_defaults(handler=_handle_sessions_get)
    sess_status = sess_sub.add_parser("status", help="update session status")
    sess_status.add_argument("--session-code", required=True)
    sess_status.add_argument("--status", required=True)
    sess_status.add_argument("--reusable-flag")
    sess_status.set_defaults(handler=_handle_sessions_status)

    return parser


def _normalize_global_args(argv: list[str] | None) -> list[str] | None:
    raw_args = list(sys.argv[1:] if argv is None else argv)
    moved: list[str] = []
    remaining: list[str] = []
    index = 0
    while index < len(raw_args):
        item = raw_args[index]
        if item in GLOBAL_OPTIONS_WITH_VALUES and index + 1 < len(raw_args):
            moved.extend([item, raw_args[index + 1]])
            index += 2
            continue
        remaining.append(item)
        index += 1
    return moved + remaining


def _add_agent_create_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--agent-code", required=True)
    parser.add_argument("--agent-name", required=True)
    parser.add_argument("--agent-engine-type", choices=["CODEX", "QODER", "CLAUDE"])
    parser.add_argument("--agent-role", required=True)
    parser.add_argument("--agent-desc", default="")
    parser.add_argument("--capability-tags", default="")
    parser.add_argument("--supported-link-types", default="")
    parser.add_argument("--callback-mode", default="PULL")
    parser.add_argument("--endpoint-url", default="")
    parser.add_argument("--status", default="ONLINE")


def _add_init_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--project-code")
    _add_agent_create_args(parser)
    parser.add_argument("--list-projects", action="store_true")


def _add_requirement_create_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--requirement-no", required=True)
    parser.add_argument("--project-code")
    parser.add_argument("--title", required=True)
    parser.add_argument("--requirement-desc", default="")
    parser.add_argument("--priority", default="")
    parser.add_argument("--status", required=True)
    parser.add_argument("--source", default="")
    parser.add_argument("--requirement-type")
    parser.add_argument("--sort-no", type=int)
    parser.add_argument("--main-agent-code", default="")
    parser.add_argument("--session-strategy")
    parser.add_argument("--preferred-session-code", default="")
    parser.add_argument("--current-stage", default="")
    parser.add_argument("--expected-deadline")
    parser.add_argument("--created-by", default="")
    parser.add_argument("--execution-mode", choices=["NORMAL", "WORKFLOW"])
    parser.add_argument("--result-extractable-flag")


def _add_requirement_update_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--title", required=True)
    parser.add_argument("--requirement-desc", default="")
    parser.add_argument("--priority", default="")
    parser.add_argument("--status", required=True)
    parser.add_argument("--source", default="")
    parser.add_argument("--sort-no", type=int)
    parser.add_argument("--main-agent-code", default="")
    parser.add_argument("--session-strategy")
    parser.add_argument("--preferred-session-code", default="")
    parser.add_argument("--current-stage", default="")
    parser.add_argument("--expected-deadline")
    parser.add_argument("--created-by", default="")
    parser.add_argument("--execution-steps", default="")
    parser.add_argument("--execution-mode", choices=["NORMAL", "WORKFLOW"])
    parser.add_argument("--result-extractable-flag")


def _add_link_create_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--link-type", required=True)
    parser.add_argument("--task-title", required=True)
    parser.add_argument("--task-desc", default="")
    parser.add_argument("--agent-code", required=True)
    parser.add_argument("--developer-name", default="")
    parser.add_argument("--status", required=True)
    parser.add_argument("--depends-on-link-id", type=int)


def _add_link_update_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--status", required=True)
    parser.add_argument("--requirement-no")
    parser.add_argument("--agent-code")
    parser.add_argument("--result-summary")
    parser.add_argument("--execution-details")
    parser.add_argument("--execution-details-file")
    parser.add_argument("--execution-details-stdin", action="store_true", help="read executionDetails from stdin without creating a local receipt file")
    parser.add_argument("--deliverable-path")
    parser.add_argument("--started-at")
    parser.add_argument("--finished-at")


def _add_session_create_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--session-code", required=True)
    parser.add_argument("--session-name", default="")
    parser.add_argument("--session-type", required=True)
    parser.add_argument("--project-code")
    parser.add_argument("--requirement-no", default="")
    parser.add_argument("--root-requirement-no", default="")
    parser.add_argument("--agent-code", required=True)
    parser.add_argument("--client-code", required=True)
    parser.add_argument("--external-session-id", default="")
    parser.add_argument("--reusable-flag")


def _handle_projects_list(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    return client.get("/projects")


def _handle_projects_get(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    project_code = _resolve_project_code(args)
    return client.get(f"/projects/{parse.quote(project_code)}")


def _handle_projects_markdown_get(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    project_code = _resolve_project_code(args)
    return client.get(f"/projects/{parse.quote(project_code)}/markdown-config")


def _handle_documents_get(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    document_id = _resolve_document_id(args)
    return {
        "document": client.get_markdown_document(document_id),
        "subtree": client.get_markdown_document_subtree(document_id),
    }


def _handle_documents_subtree(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    document_id = _resolve_document_id(args)
    return client.get_markdown_document_subtree(document_id)


def _handle_documents_path(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    document_id = _resolve_document_id(args)
    return client.get_markdown_document_path(document_id)


def _handle_documents_refs(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    document_id = _resolve_document_id(args)
    return client.get_markdown_document_refs(document_id)


def _handle_init(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    project_code = _resolve_project_code(args)
    agent_engine_type = _resolve_agent_engine_type(args)
    agent = _ensure_agent(client, args)
    if args.list_projects:
        projects = client.get("/projects")
        matching = [item for item in projects or [] if item.get("projectCode") == project_code]
        if not matching:
            raise ValueError(f"project not found: {project_code}")
        project = matching[0]
    else:
        project = client.get(f"/projects/{parse.quote(project_code)}")
    config = load_skill_config(required=False)
    config.update(
        {
            "projectCode": project_code,
            "agentCode": args.agent_code,
            "agentName": args.agent_name,
            "agentEngineType": agent_engine_type,
            "agentRole": args.agent_role,
            "baseUrl": args.base_url,
        }
    )
    save_skill_config(config)
    return {
        "project": project,
        "agent": agent,
        "projectCode": project_code,
        "agentCode": args.agent_code,
        "configPath": str(CONFIG_PATH),
        "workspaceDir": str(_active_workspace_dir()),
        "defaultsSaved": True,
    }


def _handle_config_show(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    return {
        "configPath": str(CONFIG_PATH),
        "workspaceDir": str(_active_workspace_dir()),
        "config": load_skill_config(),
    }


def _handle_config_set_project(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    config = load_skill_config(required=False)
    config["projectCode"] = args.project_code
    if args.base_url:
        config["baseUrl"] = args.base_url
    save_skill_config(config)
    return {
        "configPath": str(CONFIG_PATH),
        "workspaceDir": str(_active_workspace_dir()),
        "projectCode": args.project_code,
        "saved": True,
    }


def _handle_config_clear(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    cleared = clear_skill_config()
    return {
        "configPath": str(CONFIG_PATH),
        "workspaceDir": str(_active_workspace_dir()),
        "cleared": cleared,
    }


def _handle_agents_get(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    agent_code = _resolve_agent_code(args)
    return client.get(f"/agents/{parse.quote(agent_code)}")


def _handle_agents_list(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    project_code = _resolve_project_code(args, required=False)
    return client.get("/agents", query={"projectCode": project_code})


def _handle_agents_ensure(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    return _ensure_agent(client, args)


def _ensure_agent(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    agent_code = _resolve_agent_code(args)
    try:
        return client.get(f"/agents/{parse.quote(agent_code)}")
    except ApiError as exc:
        if exc.http_status != 404 and exc.api_code != 404:
            raise
    project_code = _resolve_project_code(args)
    agent_engine_type = _resolve_agent_engine_type(args)
    payload = {
        "projectCode": project_code,
        "agentCode": agent_code,
        "agentName": args.agent_name,
        "agentEngineType": agent_engine_type,
        "agentRole": args.agent_role,
        "agentDesc": args.agent_desc,
        "capabilityTags": args.capability_tags,
        "supportedLinkTypes": args.supported_link_types,
        "callbackMode": args.callback_mode,
        "endpointUrl": args.endpoint_url,
        "status": args.status,
    }
    try:
        return client.post("/agents", payload)
    except ApiError as exc:
        if exc.http_status == 400 and exc.api_code == 400 and "already exists" in exc.message:
            return client.get(f"/agents/{parse.quote(agent_code)}")
        raise


def _handle_agents_next(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    agent_code = _resolve_agent_code(args)
    plan = _build_agent_execution_plan(client, agent_code)
    if args.claim:
        claim_result = _claim_current_task(client, plan)
        plan = _build_agent_execution_plan(client, agent_code)
        plan["claimResult"] = claim_result
    return plan


def _handle_agents_tasks(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    agent_code = _resolve_agent_code(args)
    return _build_agent_execution_plan(client, agent_code).get("allTasks", [])


def _handle_requirements_get(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    return client.get(f"/requirements/{parse.quote(args.requirement_no)}")


def _handle_requirements_list(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    project_code = _resolve_project_code(args, required=False)
    agent_code = args.agent_code or (_resolve_agent_code(args, required=False) if args.open_only else None)
    return client.get("/requirements", query={
        "projectCode": project_code,
        "openOnly": "true" if args.open_only else None,
        "agentCode": agent_code,
    })


def _handle_requirements_workflow_results(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    return client.get(f"/requirements/{parse.quote(args.requirement_no)}/workflow-results")


def _handle_requirements_inspection(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    return client.get(f"/requirements/{parse.quote(args.requirement_no)}/inspection")


def _handle_requirements_links(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    return client.get(f"/requirements/{parse.quote(args.requirement_no)}/links")


def _handle_requirements_create(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    payload = _requirement_payload_from_args(args)
    return client.post("/requirements", payload)


def _handle_requirements_child(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    payload = _requirement_payload_from_args(args)
    return client.post(f"/requirements/{parse.quote(args.master_requirement_no)}/children", payload)


def _handle_requirements_children(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    return client.get(f"/requirements/{parse.quote(args.master_requirement_no)}/children")


def _handle_requirements_update(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    payload = _requirement_update_payload_from_args(args)
    return client.put(f"/requirements/{parse.quote(args.requirement_no)}", payload)


def _handle_requirements_delete(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    return client._request("DELETE", f"/requirements/{parse.quote(args.requirement_no)}")


def _handle_requirements_status(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    return client.put(f"/requirements/{parse.quote(args.requirement_no)}/status", {"status": args.status})


def _claim_current_task(client: AiApiSkillClient, plan: Mapping[str, Any]) -> dict[str, Any]:
    current_task = plan.get("currentTask")
    if not isinstance(current_task, Mapping):
        return {"claimed": False, "reason": "currentTask is empty"}
    if _is_acp_task_context():
        return {"claimed": False, "reason": "ACP task context already manages execution status"}

    requirement_no = str(current_task.get("requirementNo") or "").strip()
    link_id = _to_int(current_task.get("id"))
    result: dict[str, Any] = {
        "claimed": True,
        "requirementNo": requirement_no or None,
        "linkId": link_id,
        "updatedAt": datetime.now().isoformat(timespec="seconds"),
    }
    if requirement_no:
        result["requirement"] = client.put(
            f"/requirements/{parse.quote(requirement_no)}/status",
            {"status": "IN_PROGRESS"},
        )
    if link_id is not None and str(current_task.get("status") or "") != "DOING":
        result["link"] = client.put(
            f"/links/{link_id}/progress",
            {
                "status": "DOING",
                "startedAt": datetime.now().isoformat(timespec="seconds"),
            },
        )
    return result


def _is_acp_task_context() -> bool:
    if str(os.getenv("AI_API_CLIENT_SESSION_TYPE") or "").strip().upper() == "ACP":
        return True
    raw_command = os.getenv("AI_API_CLIENT_COMMAND_JSON")
    if not raw_command:
        return False
    try:
        command = json.loads(raw_command)
    except json.JSONDecodeError:
        return False
    if not isinstance(command, Mapping):
        return False
    values = [
        command.get("sessionType"),
        command.get("runtimeType"),
        command.get("agentRuntimeType"),
    ]
    return any(str(value or "").strip().upper() == "ACP" for value in values)


def _handle_requirements_session_preference(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    payload = {}
    if args.session_strategy:
        payload["sessionStrategy"] = args.session_strategy
    if args.preferred_session_code:
        payload["preferredSessionCode"] = args.preferred_session_code
    return client.put(f"/requirements/{parse.quote(args.requirement_no)}/session-preference", payload)


def _handle_requirements_link_create(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    payload = {
        "linkType": args.link_type,
        "taskTitle": args.task_title,
        "taskDesc": args.task_desc,
        "agentCode": args.agent_code,
        "developerName": args.developer_name,
        "status": args.status,
        "dependsOnLinkId": args.depends_on_link_id,
    }
    return client.post(f"/requirements/{parse.quote(args.requirement_no)}/links", payload)


def _handle_links_update(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    current = _find_link_for_safe_update(client, args.link_id, args.requirement_no, args.agent_code)
    execution_details = _resolve_text_value(
        direct_value=args.execution_details,
        file_path=args.execution_details_file,
        stdin_flag=args.execution_details_stdin,
        field_name="executionDetails",
    )
    payload = {
        "status": args.status,
        "resultSummary": current.get("resultSummary") if args.result_summary is None else args.result_summary,
        "executionDetails": current.get("executionDetails") if execution_details is None else execution_details,
        "deliverablePath": current.get("deliverablePath") if args.deliverable_path is None else args.deliverable_path,
        "startedAt": current.get("startedAt") if args.started_at is None else args.started_at,
        "finishedAt": current.get("finishedAt") if args.finished_at is None else args.finished_at,
    }
    return client.put(f"/links/{args.link_id}/progress", payload)


def _handle_sessions_create(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    project_code = _resolve_project_code(args)
    payload = {
        "sessionCode": args.session_code,
        "sessionName": args.session_name,
        "sessionType": args.session_type,
        "projectCode": project_code,
        "requirementNo": args.requirement_no,
        "rootRequirementNo": args.root_requirement_no,
        "agentCode": args.agent_code,
        "clientCode": args.client_code,
        "externalSessionId": args.external_session_id,
        "reusableFlag": _parse_bool(args.reusable_flag),
    }
    return client.post("/sessions", payload)


def _handle_sessions_list(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    project_code = _resolve_project_code(args, required=False)
    query = {
        "projectCode": project_code,
        "agentCode": args.agent_code,
        "clientCode": args.client_code,
        "rootRequirementNo": args.root_requirement_no,
        "reusableFlag": args.reusable_flag,
        "status": args.status,
    }
    return client.get("/sessions", query=query)


def _handle_sessions_get(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    return client.get(f"/sessions/{parse.quote(args.session_code)}")


def _handle_sessions_status(client: AiApiSkillClient, args: argparse.Namespace) -> Any:
    payload = {
        "status": args.status,
        "reusableFlag": _parse_bool(args.reusable_flag),
    }
    return client.put(f"/sessions/{parse.quote(args.session_code)}/status", payload)


def _requirement_payload_from_args(args: argparse.Namespace) -> dict[str, Any]:
    project_code = _resolve_project_code(args)
    payload = {
        "requirementNo": getattr(args, "requirement_no", None),
        "projectCode": project_code,
        "title": args.title,
        "requirementDesc": args.requirement_desc,
        "priority": args.priority,
        "status": args.status,
        "source": args.source,
        "requirementType": args.requirement_type,
        "sortNo": args.sort_no,
        "mainAgentCode": args.main_agent_code,
        "sessionStrategy": args.session_strategy,
        "preferredSessionCode": args.preferred_session_code,
        "currentStage": args.current_stage,
        "expectedDeadline": args.expected_deadline,
        "createdBy": args.created_by,
        "executionMode": args.execution_mode,
        "resultExtractableFlag": _parse_bool(args.result_extractable_flag),
    }
    return {k: v for k, v in payload.items() if v not in ("", None)}


def _requirement_update_payload_from_args(args: argparse.Namespace) -> dict[str, Any]:
    payload = {
        "title": args.title,
        "requirementDesc": args.requirement_desc,
        "priority": args.priority,
        "status": args.status,
        "source": args.source,
        "sortNo": args.sort_no,
        "mainAgentCode": args.main_agent_code,
        "sessionStrategy": args.session_strategy,
        "preferredSessionCode": args.preferred_session_code,
        "currentStage": args.current_stage,
        "expectedDeadline": args.expected_deadline,
        "createdBy": args.created_by,
        "executionSteps": args.execution_steps,
        "executionMode": args.execution_mode,
        "resultExtractableFlag": _parse_bool(args.result_extractable_flag),
    }
    return {k: v for k, v in payload.items() if v not in ("", None)}


def _find_link_for_safe_update(
    client: AiApiSkillClient,
    link_id: str,
    requirement_no: str | None,
    agent_code: str | None,
) -> dict[str, Any]:
    numeric_id = _to_int(link_id)
    if numeric_id is None:
        raise ValueError(f"invalid link id: {link_id}")
    if requirement_no:
        links = client.get(f"/requirements/{parse.quote(requirement_no)}/links")
        for item in links or []:
            if item.get("id") == numeric_id:
                return item
        raise ValueError(f"link {link_id} not found under requirement {requirement_no}")
    if agent_code:
        tasks = client.get(f"/agents/{parse.quote(agent_code)}/tasks")
        for item in tasks or []:
            if item.get("id") == numeric_id:
                return item
        raise ValueError(f"link {link_id} not found under agent {agent_code} tasks")
    raise ValueError("safe link update requires --requirement-no or --agent-code")


def _parse_bool(value: str | None) -> bool | None:
    if value is None:
        return None
    lowered = value.strip().lower()
    if lowered in {"1", "true", "yes", "y"}:
        return True
    if lowered in {"0", "false", "no", "n"}:
        return False
    raise ValueError(f"invalid boolean value: {value}")


def _resolve_text_value(*, direct_value: str | None, file_path: str | None, stdin_flag: bool, field_name: str) -> str | None:
    input_count = sum(value is not None for value in (direct_value, file_path)) + (1 if stdin_flag else 0)
    if input_count > 1:
        raise ValueError(f"{field_name} cannot use multiple input sources")
    if stdin_flag:
        return sys.stdin.read()
    if file_path is None:
        return direct_value
    try:
        return Path(file_path).read_text(encoding="utf-8")
    except OSError as exc:
        raise ValueError(f"failed to read {field_name} file: {exc}") from exc


def _resolve_agent_code(args: argparse.Namespace, required: bool = True) -> str | None:
    cli_value = getattr(args, "agent_code", None)
    if cli_value:
        return cli_value
    env_value = os.getenv("AI_API_SKILL_AGENT_CODE")
    if env_value:
        return env_value
    config_value = load_skill_config().get("agentCode")
    if config_value:
        return str(config_value)
    if required:
        raise ValueError(
            "agentCode not configured. Use --agent-code or run `init` first."
        )
    return None


def _resolve_agent_engine_type(args: argparse.Namespace) -> str:
    cli_value = getattr(args, "agent_engine_type", None)
    if cli_value:
        return str(cli_value).strip().upper()
    try:
        config_value = str(load_skill_config().get("agentEngineType") or "").strip().upper()
    except ValueError:
        config_value = ""
    if config_value in {"CODEX", "QODER", "CLAUDE"}:
        return config_value
    return "QODER"


def _resolve_project_code(args: argparse.Namespace, required: bool = True) -> str | None:
    cli_value = getattr(args, "project_code", None)
    if cli_value:
        return cli_value
    env_value = os.getenv("AI_API_SKILL_PROJECT_CODE")
    if env_value:
        return env_value
    config_value = load_skill_config().get("projectCode")
    if config_value:
        return str(config_value)
    if required:
        raise ValueError(
            "projectCode not configured. Use --project-code, "
            "`config set-project`, or run `init` first."
        )
    return None


def _resolve_document_id(args: argparse.Namespace) -> str:
    document_id = str(getattr(args, "document_id", "") or "").strip()
    if not document_id:
        raise ValueError("documentId is required")
    return document_id


def _build_agent_execution_plan(client: AiApiSkillClient, agent_code: str) -> dict[str, Any]:
    tasks = client.get(f"/agents/{parse.quote(agent_code)}/tasks") or []
    open_requirements = client.get("/requirements", query={
        "openOnly": "true",
        "agentCode": agent_code,
    }) or []
    linked_requirement_nos = {
        str(item.get("requirementNo"))
        for item in tasks
        if item.get("requirementNo")
    }
    for requirement in open_requirements:
        if not isinstance(requirement, Mapping):
            continue
        requirement_no = str(requirement.get("requirementNo") or "").strip()
        if not requirement_no or requirement_no in linked_requirement_nos:
            continue
        if str(requirement.get("requirementType") or "").upper() != "SUB":
            continue
        tasks.append({
            "id": None,
            "taskSource": "REQUIREMENT_MODULE",
            "requirementNo": requirement_no,
            "linkType": None,
            "taskTitle": requirement.get("title"),
            "taskDesc": requirement.get("requirementDesc") or requirement.get("executionSteps"),
            "agentCode": requirement.get("mainAgentCode"),
            "status": requirement.get("status"),
            "resultSummary": None,
            "executionDetails": None,
            "deliverablePath": None,
            "dependsOnLinkId": None,
            "createdAt": requirement.get("createdAt"),
            "updatedAt": requirement.get("updatedAt"),
            "rootRequirementNo": requirement.get("rootRequirementNo"),
            "executionMode": requirement.get("executionMode"),
            "reviewRequiredFlag": requirement.get("reviewRequiredFlag"),
            "reviewApprovedFlag": requirement.get("reviewApprovedFlag"),
        })
    requirement_nos = sorted(
        {
            str(item.get("requirementNo"))
            for item in tasks
            if item.get("requirementNo")
        }
    )
    links_by_requirement: dict[str, list[dict[str, Any]]] = {}
    link_index: dict[int, dict[str, Any]] = {}
    for requirement_no in requirement_nos:
        links = client.get(f"/requirements/{parse.quote(requirement_no)}/links") or []
        normalized_links = [dict(item) for item in links]
        links_by_requirement[requirement_no] = normalized_links
        for link in normalized_links:
            link_id = _to_int(link.get("id"))
            if link_id is not None:
                link_index[link_id] = link

    same_agent_children: dict[int, list[int]] = {}
    for task in tasks:
        dependency_id = _to_int(task.get("dependsOnLinkId"))
        task_id = _to_int(task.get("id"))
        if dependency_id is None or task_id is None:
            continue
        same_agent_children.setdefault(dependency_id, []).append(task_id)

    decorated_tasks = [
        _decorate_agent_task(dict(task), link_index, same_agent_children)
        for task in tasks
    ]
    decorated_tasks.sort(key=_task_sort_key)
    for index, task in enumerate(decorated_tasks, start=1):
        task["executionOrder"] = index

    ready_tasks = [task for task in decorated_tasks if task.get("queueState") == "READY"]
    waiting_tasks = [task for task in decorated_tasks if task.get("queueState") == "WAITING"]
    blocked_tasks = [task for task in decorated_tasks if task.get("queueState") == "BLOCKED"]
    current_task = next((task for task in ready_tasks if task.get("status") in {"DOING", "IN_PROGRESS"}), None)
    if current_task is None and ready_tasks:
        current_task = ready_tasks[0]

    return {
        "agentCode": agent_code,
        "projectCode": load_skill_config().get("projectCode"),
        "currentTask": current_task,
        "readyTasks": ready_tasks,
        "waitingTasks": waiting_tasks,
        "blockedTasks": blocked_tasks,
        "allTasks": decorated_tasks,
        "hint": "Execute currentTask first. After updating DONE, rerun `agents next` and continue while readyTasks is not empty.",
    }


def _decorate_agent_task(
    task: dict[str, Any],
    link_index: Mapping[int, dict[str, Any]],
    same_agent_children: Mapping[int, list[int]],
) -> dict[str, Any]:
    dependency_id = _to_int(task.get("dependsOnLinkId"))
    dependency_link = link_index.get(dependency_id) if dependency_id is not None else None
    dependency_status = dependency_link.get("status") if dependency_link else None
    dependency_resolved = dependency_id is None or dependency_status in {"DONE", "SKIPPED"}
    queue_state = "READY"
    blocking_reason = None

    if (
        task.get("taskSource") == "REQUIREMENT_MODULE"
        and task.get("reviewRequiredFlag")
        and not task.get("reviewApprovedFlag")
    ):
        queue_state = "WAITING"
        blocking_reason = "waiting for manual review approval"
    elif task.get("status") == "BLOCKED":
        queue_state = "BLOCKED"
        blocking_reason = task.get("resultSummary") or "link status is BLOCKED"
    elif dependency_id is not None and dependency_link is None:
        queue_state = "WAITING"
        blocking_reason = f"waiting for dependency link {dependency_id}"
    elif not dependency_resolved:
        queue_state = "WAITING"
        blocking_reason = (
            f"waiting for link {dependency_id} "
            f"({dependency_link.get('linkType')}) status {dependency_status}"
        )

    task["dependencyStatus"] = dependency_status
    task["dependencyResolved"] = dependency_resolved
    task["dependencyLinkType"] = dependency_link.get("linkType") if dependency_link else None
    task["dependencyAgentCode"] = dependency_link.get("agentCode") if dependency_link else None
    task["queueState"] = queue_state
    task["readyNow"] = queue_state == "READY"
    task["blockingReason"] = blocking_reason
    task["executionDepth"] = _dependency_depth(task, link_index)
    task["nextTaskIdsSameAgent"] = same_agent_children.get(_to_int(task.get("id")) or -1, [])
    return task


def _dependency_depth(task: Mapping[str, Any], link_index: Mapping[int, Mapping[str, Any]]) -> int:
    depth = 0
    current_dependency = _to_int(task.get("dependsOnLinkId"))
    visited: set[int] = set()
    while current_dependency is not None and current_dependency not in visited:
        visited.add(current_dependency)
        depth += 1
        parent = link_index.get(current_dependency)
        if parent is None:
            break
        current_dependency = _to_int(parent.get("dependsOnLinkId"))
    return depth


def _task_sort_key(task: Mapping[str, Any]) -> tuple[Any, ...]:
    queue_rank = {"READY": 0, "WAITING": 1, "BLOCKED": 2}
    status_rank = {"DOING": 0, "IN_PROGRESS": 0, "TODO": 1, "PENDING": 1, "BLOCKED": 2}
    return (
        queue_rank.get(str(task.get("queueState")), 9),
        status_rank.get(str(task.get("status")), 9),
        str(task.get("requirementNo") or ""),
        int(task.get("executionDepth") or 0),
        str(task.get("createdAt") or ""),
        _to_int(task.get("id")) or 0,
    )


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(_normalize_global_args(argv))
    try:
        _set_active_workspace_dir(args.workspace_dir)
        allow_missing_config = args.resource == "init" or (
            args.resource == "config" and args.action in {"set-project", "clear"}
        )
        config = load_skill_config(required=not allow_missing_config)
        if config.get("workspaceDir"):
            _set_active_workspace_dir(str(config["workspaceDir"]))
        base_url = args.base_url or os.getenv("AI_API_SKILL_BASE_URL") or config.get("baseUrl") or "http://localhost:8080"
        args.base_url = base_url
        client = AiApiSkillClient(base_url, timeout=args.timeout)
        result = args.handler(client, args)
        print_json(result)
        return 0
    except (ApiError, ValueError) as exc:
        print(str(exc), file=__import__("sys").stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
