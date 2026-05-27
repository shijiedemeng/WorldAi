#!/usr/bin/env python3
from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass, field
import json
import os
from pathlib import Path
import sys
from typing import Any, Mapping
from urllib import error, parse, request

SKILL_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = SKILL_ROOT / "skill-config.json"
DEFAULT_MD_DIRNAME = "md-files"
COMMON_SCOPE = "common"
SUPPORTED_AGENT_ROLES = {"MAIN", "DEVELOPER", "TESTER", "REVIEWER", "OPS"}
GLOBAL_OPTIONS_WITH_VALUES = {"--url", "--project-code", "--agent-role", "--workspace-dir", "--timeout"}


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


class ProjectMarkdownSyncError(Exception):
    """Base error for workspace Markdown synchronization."""


class ProjectMarkdownPathError(ProjectMarkdownSyncError):
    """Raised when a server path is not safe to materialize locally."""


class ProjectMarkdownConflict(ProjectMarkdownSyncError):
    def __init__(self, report: "ProjectMarkdownSyncReport") -> None:
        self.report = report
        conflicts = ", ".join(report.conflict_files) or "unknown"
        super().__init__(f"markdown sync conflict: {conflicts}")


@dataclass
class ProjectMarkdownSyncReport:
    project_code: str
    agent_role: str
    markdown_sync_mode: str
    md_root_dir: str
    scopes: list[str] = field(default_factory=list)
    written_files: list[str] = field(default_factory=list)
    skipped_files: list[str] = field(default_factory=list)
    conflict_files: list[str] = field(default_factory=list)
    loaded_files: list[str] = field(default_factory=list)
    file_contents: dict[str, str] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class MarkdownLoadReport:
    md_root_dir: str
    scopes: list[str]
    loaded_files: list[str]
    file_contents: dict[str, str]

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


class ProjectMarkdownWorkspaceClient:
    def __init__(self, base_url: str, timeout: float = 10.0) -> None:
        normalized = base_url.rstrip("/")
        self.api_base = normalized if normalized.endswith("/api") else f"{normalized}/api"
        self.timeout = timeout

    def get_project_markdown_config(self, project_code: str) -> dict[str, Any]:
        data = self._request("GET", f"/projects/{parse.quote(project_code)}/markdown-config")
        return data if isinstance(data, dict) else {}

    def save_project_markdown_config(
        self,
        project_code: str,
        payload: Mapping[str, Any],
    ) -> dict[str, Any]:
        data = self._request("PUT", f"/projects/{parse.quote(project_code)}/markdown-config", payload=payload)
        return data if isinstance(data, dict) else {}

    def _request(
        self,
        method: str,
        path: str,
        payload: Mapping[str, Any] | None = None,
        query: Mapping[str, Any] | None = None,
    ) -> Any:
        url = f"{self.api_base}{path if path.startswith('/') else '/' + path}"
        if query:
            query_items = [(key, str(value)) for key, value in query.items() if value is not None]
            if query_items:
                url = f"{url}?{parse.urlencode(query_items)}"

        headers = {"Accept": "application/json"}
        body = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")

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
        api_code = _to_int(parsed.get("code"))
        if api_code != 0:
            raise ApiError(
                message=str(parsed.get("message", "application error")),
                api_code=api_code,
                body=raw,
            )
        return parsed.get("data")


def _resolve_workspace_dir_value(args: argparse.Namespace | None = None) -> Path:
    cli_value = getattr(args, "workspace_dir", None) if args else None
    raw = cli_value or os.getenv("PROJECT_MARKDOWN_WORKSPACE_DIR") or os.getcwd()
    return Path(raw).expanduser().resolve()


def _read_config_file() -> dict[str, Any]:
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
            f"当前工作目录没有匹配的 Markdown skill 配置：{workspace_dir}。"
            f"请在 {CONFIG_PATH} 的 workspaces 中新增 workspaceDir。"
        )
    return {}


def _load_config(args: argparse.Namespace | None = None, *, required: bool = True) -> dict[str, Any]:
    raw_config = _read_config_file()
    if not raw_config:
        if required:
            raise ValueError(f"skill config not found: {CONFIG_PATH}")
        return {}
    return _select_workspace_config(raw_config, _resolve_workspace_dir_value(args), required=required)


def _parse_json(raw: str) -> Any:
    if not raw.strip():
        return {}
    return json.loads(raw)


def _to_int(value: Any) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _normalize_agent_role(value: str | None) -> str:
    normalized = str(value or "").strip().upper()
    if normalized not in SUPPORTED_AGENT_ROLES:
        raise ValueError(
            "agentRole not configured or invalid. Expected one of "
            f"{sorted(SUPPORTED_AGENT_ROLES)}."
        )
    return normalized


def _scope_name_for_role(agent_role: str) -> str:
    return agent_role.lower()


def _resolve_url(args: argparse.Namespace, config: dict[str, Any]) -> str:
    return (
        args.url
        or os.getenv("PROJECT_MARKDOWN_WORKSPACE_URL")
        or str(config.get("url") or "").strip()
        or "http://localhost:8080"
    )


def _resolve_project_code(args: argparse.Namespace, config: dict[str, Any]) -> str:
    project_code = (
        args.project_code
        or os.getenv("PROJECT_MARKDOWN_WORKSPACE_PROJECT_CODE")
        or str(config.get("projectCode") or "").strip()
    )
    if not project_code:
        raise ValueError(
            "projectCode not configured. Use --project-code or set "
            "skills/project-markdown-workspace/skill-config.json first."
        )
    return project_code


def _resolve_agent_role(args: argparse.Namespace, config: dict[str, Any]) -> str:
    return _normalize_agent_role(
        args.agent_role
        or os.getenv("PROJECT_MARKDOWN_WORKSPACE_AGENT_ROLE")
        or config.get("agentRole")
    )


def _resolve_workspace_dir(args: argparse.Namespace, config: Mapping[str, Any] | None = None) -> Path:
    configured = str((config or {}).get("workspaceDir") or "").strip()
    if configured:
        return Path(configured).expanduser().resolve()
    return _resolve_workspace_dir_value(args)


def _resolve_md_root_dir(args: argparse.Namespace, config: Mapping[str, Any] | None = None) -> Path:
    workspace_dir = _resolve_workspace_dir(args, config)
    return workspace_dir / DEFAULT_MD_DIRNAME


def _print_json(data: Any) -> None:
    print(json.dumps(data, ensure_ascii=False, indent=2, default=str))


def _filter_markdown_files(markdown_config: dict[str, Any], agent_role: str) -> list[dict[str, Any]]:
    filtered: list[dict[str, Any]] = []
    for file_info in markdown_config.get("markdownFiles") or []:
        if not isinstance(file_info, dict):
            continue
        scope_role = file_info.get("agentRole")
        if scope_role is None:
            filtered.append(file_info)
            continue
        if str(scope_role).strip().upper() == agent_role:
            filtered.append(file_info)
    return filtered


def _relative_storage_path(file_info: Mapping[str, Any], agent_role: str) -> str:
    relative_path = str(file_info.get("filePath") or "").strip().replace("\\", "/")
    if not relative_path:
        raise ProjectMarkdownPathError("markdown filePath is empty")
    if relative_path.startswith("/") or relative_path.startswith("./"):
        raise ProjectMarkdownPathError(f"markdown path must be relative: {relative_path}")
    if relative_path == ".." or "../" in relative_path:
        raise ProjectMarkdownPathError(f"markdown path escapes workspace: {relative_path}")

    scope_role = file_info.get("agentRole")
    if scope_role is None:
        return f"{COMMON_SCOPE}/{relative_path}"

    normalized_scope = str(scope_role).strip().lower()
    if normalized_scope != _scope_name_for_role(agent_role):
        raise ProjectMarkdownPathError(
            f"unexpected agentRole scope for current role {agent_role}: {scope_role}"
        )

    scoped_relative = relative_path
    scope_prefix = f"roles/{normalized_scope}/"
    if scoped_relative.startswith(scope_prefix):
        scoped_relative = scoped_relative[len(scope_prefix):]
    return f"{normalized_scope}/{scoped_relative}"


def _resolve_md_file_path(md_root_dir: Path, relative_storage_path: str) -> Path:
    candidate = (md_root_dir / relative_storage_path).resolve()
    root = md_root_dir.resolve()
    if candidate != root and root not in candidate.parents:
        raise ProjectMarkdownPathError(f"markdown path escapes md root: {relative_storage_path}")
    return candidate


def _resolve_upload_target(args: argparse.Namespace, agent_role: str, md_root_dir: Path) -> tuple[Path, str]:
    target = str(args.file or "").strip().replace("\\", "/")
    if not target:
        raise ValueError("--file is required for upload")
    scope_name = _scope_name_for_role(agent_role)

    prefixed_role_path = f"{scope_name}/"
    prefixed_common_path = f"{COMMON_SCOPE}/"
    if target.startswith(prefixed_role_path):
        relative_storage_path = target
    elif target.startswith(prefixed_common_path):
        relative_storage_path = target
    else:
        relative_storage_path = f"{scope_name}/{target}"

    local_path = _resolve_md_file_path(md_root_dir, relative_storage_path)
    return local_path, relative_storage_path


def _to_server_markdown_entry(relative_storage_path: str, content: str, agent_role: str) -> dict[str, Any]:
    normalized = relative_storage_path.replace("\\", "/")
    if normalized.startswith(f"{COMMON_SCOPE}/"):
        return {
            "agentRole": None,
            "fileType": "EXTRA",
            "filePath": normalized[len(f"{COMMON_SCOPE}/"):],
            "content": content,
        }

    scope_name = _scope_name_for_role(agent_role)
    scope_prefix = f"{scope_name}/"
    if not normalized.startswith(scope_prefix):
        raise ValueError(f"upload path is outside current role scope: {relative_storage_path}")
    return {
        "agentRole": agent_role,
        "fileType": "EXTRA",
        "filePath": normalized[len(scope_prefix):],
        "content": content,
    }


def _merge_markdown_entry(
    markdown_files: list[dict[str, Any]],
    new_entry: dict[str, Any],
) -> list[dict[str, Any]]:
    merged: list[dict[str, Any]] = []
    replaced = False
    new_role = new_entry.get("agentRole")
    new_type = new_entry.get("fileType")
    new_base_key = new_entry.get("baseKey")
    new_path = new_entry.get("filePath")

    for item in markdown_files:
        if not isinstance(item, dict):
            continue
        same_entry = (
            item.get("agentRole") == new_role
            and item.get("fileType") == new_type
            and item.get("baseKey") == new_base_key
            and item.get("filePath") == new_path
        )
        if same_entry:
            merged.append(new_entry)
            replaced = True
        else:
            merged.append(item)

    if not replaced:
        merged.append(new_entry)
    return merged


def load_local_markdowns(md_root_dir: Path, agent_role: str) -> MarkdownLoadReport:
    scopes = [COMMON_SCOPE, _scope_name_for_role(agent_role)]
    loaded_files: list[str] = []
    file_contents: dict[str, str] = {}

    for scope in scopes:
        scope_dir = md_root_dir / scope
        if not scope_dir.exists():
            scope_dir.mkdir(parents=True, exist_ok=True)
        for path in sorted(scope_dir.rglob("*.md")):
            if path.is_dir():
                continue
            relative_path = str(path.relative_to(md_root_dir)).replace("\\", "/")
            file_contents[relative_path] = path.read_text(encoding="utf-8")
            loaded_files.append(relative_path)

    return MarkdownLoadReport(
        md_root_dir=str(md_root_dir),
        scopes=scopes,
        loaded_files=loaded_files,
        file_contents=file_contents,
    )


def sync_project_markdowns(
    client: ProjectMarkdownWorkspaceClient,
    project_code: str,
    agent_role: str,
    md_root_dir: Path,
) -> ProjectMarkdownSyncReport:
    markdown_config = client.get_project_markdown_config(project_code)
    sync_mode = str(markdown_config.get("markdownSyncMode") or "CANCEL")
    filtered_files = _filter_markdown_files(markdown_config, agent_role)
    report = ProjectMarkdownSyncReport(
        project_code=project_code,
        agent_role=agent_role,
        markdown_sync_mode=sync_mode,
        md_root_dir=str(md_root_dir),
        scopes=[COMMON_SCOPE, _scope_name_for_role(agent_role)],
    )

    for scope in report.scopes:
        (md_root_dir / scope).mkdir(parents=True, exist_ok=True)

    for file_info in filtered_files:
        content = str(file_info.get("content") or "")
        if not content.strip():
            continue

        relative_storage_path = _relative_storage_path(file_info, agent_role)
        local_path = _resolve_md_file_path(md_root_dir, relative_storage_path)
        if local_path.exists():
            if local_path.is_dir():
                raise ProjectMarkdownPathError(f"target path is a directory: {relative_storage_path}")
            existing = local_path.read_text(encoding="utf-8")
            if existing == content:
                report.skipped_files.append(relative_storage_path)
                continue
            if sync_mode == "CANCEL":
                report.conflict_files.append(relative_storage_path)
                continue

        local_path.parent.mkdir(parents=True, exist_ok=True)
        local_path.write_text(content, encoding="utf-8")
        report.written_files.append(relative_storage_path)

    if report.conflict_files:
        raise ProjectMarkdownConflict(report)

    load_report = load_local_markdowns(md_root_dir, agent_role)
    report.loaded_files = load_report.loaded_files
    report.file_contents = load_report.file_contents
    return report


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Synchronize ai-api project markdowns into a local md-files workspace")
    parser.add_argument("--url")
    parser.add_argument("--project-code")
    parser.add_argument("--agent-role")
    parser.add_argument("--workspace-dir")
    parser.add_argument("--timeout", type=float, default=10.0)

    subparsers = parser.add_subparsers(dest="command", required=True)

    config = subparsers.add_parser("config", help="show resolved defaults")
    config.set_defaults(handler=_handle_config)

    init_cmd = subparsers.add_parser("init", help="initialize and load local md-files for common and current role")
    init_cmd.set_defaults(handler=_handle_init)

    fetch = subparsers.add_parser("fetch", help="fetch filtered server markdown config for common and current role")
    fetch.set_defaults(handler=_handle_fetch)

    sync = subparsers.add_parser("sync", help="sync filtered markdowns into md-files and reload them")
    sync.set_defaults(handler=_handle_sync)

    load_cmd = subparsers.add_parser("load", help="load all local md-files under common and current role")
    load_cmd.set_defaults(handler=_handle_load)

    upload_cmd = subparsers.add_parser("upload", help="upload one local md file under current role scope")
    upload_cmd.add_argument("--file", required=True, help="relative path under md-files/<role>/ or md-files/common/")
    upload_cmd.set_defaults(handler=_handle_upload)

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


def _handle_config(args: argparse.Namespace) -> dict[str, Any]:
    config = _load_config(args)
    agent_role = _resolve_agent_role(args, config)
    workspace_dir = _resolve_workspace_dir(args, config)
    md_root_dir = _resolve_md_root_dir(args, config)
    return {
        "configPath": str(CONFIG_PATH),
        "config": config,
        "resolved": {
            "url": _resolve_url(args, config),
            "projectCode": _resolve_project_code(args, config),
            "agentRole": agent_role,
            "workspaceDir": str(workspace_dir),
            "mdRootDir": str(md_root_dir),
            "scopes": [COMMON_SCOPE, _scope_name_for_role(agent_role)],
        },
    }


def _handle_init(args: argparse.Namespace) -> dict[str, Any]:
    config = _load_config(args)
    agent_role = _resolve_agent_role(args, config)
    md_root_dir = _resolve_md_root_dir(args, config)
    load_report = load_local_markdowns(md_root_dir, agent_role)
    data = load_report.to_dict()
    data["agentRole"] = agent_role
    data["projectCode"] = _resolve_project_code(args, config)
    return data


def _handle_fetch(args: argparse.Namespace) -> dict[str, Any]:
    config = _load_config(args)
    agent_role = _resolve_agent_role(args, config)
    client = ProjectMarkdownWorkspaceClient(_resolve_url(args, config), timeout=args.timeout)
    project_code = _resolve_project_code(args, config)
    markdown_config = client.get_project_markdown_config(project_code)
    filtered_files = _filter_markdown_files(markdown_config, agent_role)
    return {
        "projectCode": project_code,
        "agentRole": agent_role,
        "url": client.api_base,
        "markdownSyncMode": markdown_config.get("markdownSyncMode") or "CANCEL",
        "scopes": [COMMON_SCOPE, _scope_name_for_role(agent_role)],
        "markdownFiles": filtered_files,
    }


def _handle_sync(args: argparse.Namespace) -> dict[str, Any]:
    config = _load_config(args)
    agent_role = _resolve_agent_role(args, config)
    client = ProjectMarkdownWorkspaceClient(_resolve_url(args, config), timeout=args.timeout)
    project_code = _resolve_project_code(args, config)
    md_root_dir = _resolve_md_root_dir(args, config)
    report = sync_project_markdowns(client, project_code, agent_role, md_root_dir)
    data = report.to_dict()
    data["url"] = client.api_base
    return data


def _handle_load(args: argparse.Namespace) -> dict[str, Any]:
    config = _load_config(args)
    agent_role = _resolve_agent_role(args, config)
    md_root_dir = _resolve_md_root_dir(args, config)
    load_report = load_local_markdowns(md_root_dir, agent_role)
    data = load_report.to_dict()
    data["agentRole"] = agent_role
    data["projectCode"] = _resolve_project_code(args, config)
    return data


def _handle_upload(args: argparse.Namespace) -> dict[str, Any]:
    config = _load_config(args)
    agent_role = _resolve_agent_role(args, config)
    client = ProjectMarkdownWorkspaceClient(_resolve_url(args, config), timeout=args.timeout)
    project_code = _resolve_project_code(args, config)
    md_root_dir = _resolve_md_root_dir(args, config)

    local_path, relative_storage_path = _resolve_upload_target(args, agent_role, md_root_dir)
    if not local_path.exists() or local_path.is_dir():
        raise ValueError(f"local markdown file not found: {local_path}")

    content = local_path.read_text(encoding="utf-8").strip()
    if not content:
        raise ValueError(f"local markdown file is empty: {local_path}")

    current_config = client.get_project_markdown_config(project_code)
    markdown_files = list(current_config.get("markdownFiles") or [])
    new_entry = _to_server_markdown_entry(relative_storage_path, content, agent_role)
    merged_files = _merge_markdown_entry(markdown_files, new_entry)
    saved = client.save_project_markdown_config(
        project_code,
        {
            "markdownSyncMode": current_config.get("markdownSyncMode") or "CANCEL",
            "markdownFiles": merged_files,
        },
    )
    return {
        "projectCode": project_code,
        "agentRole": agent_role,
        "uploadedFile": relative_storage_path,
        "serverFilePath": new_entry.get("filePath"),
        "url": client.api_base,
        "markdownConfig": saved,
    }


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(_normalize_global_args(argv))

    try:
        result = args.handler(args)
        _print_json(result)
        return 0
    except ProjectMarkdownConflict as exc:
        _print_json(
            {
                "error": "ProjectMarkdownConflict",
                "message": str(exc),
                "report": exc.report.to_dict(),
            }
        )
        return 2
    except (ApiError, ProjectMarkdownSyncError, OSError, ValueError) as exc:
        _print_json(
            {
                "error": type(exc).__name__,
                "message": str(exc),
            }
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
