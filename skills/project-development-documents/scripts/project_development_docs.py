#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys
from typing import Any, Mapping
from urllib import error, parse, request

SKILL_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = SKILL_ROOT / "skill-config.json"
GLOBAL_OPTIONS_WITH_VALUES = {"--url", "--project-code", "--workspace-dir", "--context-url", "--timeout", "--agent-role"}


class DevelopmentDocumentClient:
    def __init__(self, base_url: str, timeout: float = 10.0) -> None:
        normalized = base_url.rstrip("/")
        self.api_base = normalized if normalized.endswith("/api") else f"{normalized}/api"
        self.timeout = timeout

    def get_document_context(self, project_code: str, usage_type: str) -> str:
        data = self._request("GET", f"/projects/{parse.quote(project_code)}/document-context/{parse.quote(usage_type)}")
        return data if isinstance(data, str) else ""

    def get_context_url(self, context_url: str) -> str:
        data = self._request_url("GET", context_url)
        return data if isinstance(data, str) else ""

    def _request(self, method: str, path: str) -> Any:
        return self._request_url(method, f"{self.api_base}{path if path.startswith('/') else '/' + path}")

    def _request_url(self, method: str, url: str) -> Any:
        req = request.Request(url, headers={"Accept": "application/json"}, method=method.upper())
        try:
            with request.urlopen(req, timeout=self.timeout) as response:
                raw = response.read().decode("utf-8")
        except error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            parsed = _parse_json(raw)
            if isinstance(parsed, dict):
                raise RuntimeError(str(parsed.get("message") or raw or exc)) from exc
            raise RuntimeError(raw or str(exc)) from exc
        except error.URLError as exc:
            raise RuntimeError(f"network error: {exc.reason}") from exc
        parsed = _parse_json(raw)
        if not isinstance(parsed, dict):
            raise RuntimeError("invalid JSON response")
        if _to_int(parsed.get("code")) != 0:
            raise RuntimeError(str(parsed.get("message") or "application error"))
        return parsed.get("data")


def _read_config_file() -> dict[str, Any]:
    if not CONFIG_PATH.exists():
        return {}
    parsed = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    return parsed if isinstance(parsed, dict) else {}


def _config_entries(raw_config: Mapping[str, Any]) -> list[dict[str, Any]]:
    workspaces = raw_config.get("workspaces")
    if isinstance(workspaces, list):
        return [dict(item) for item in workspaces if isinstance(item, dict)]
    return [dict(raw_config)] if raw_config else []


def _workspace_dir(args: argparse.Namespace | None = None) -> Path:
    raw = getattr(args, "workspace_dir", None) if args else None
    return Path(raw or os.getenv("PROJECT_DEVELOPMENT_DOCS_WORKSPACE_DIR") or os.getcwd()).expanduser().resolve()


def _select_config(args: argparse.Namespace, *, required: bool = True) -> dict[str, Any]:
    workspace_dir = _workspace_dir(args)
    candidates: list[tuple[int, dict[str, Any]]] = []
    for entry in _config_entries(_read_config_file()):
        raw_dir = str(entry.get("workspaceDir") or entry.get("workspace_dir") or "").strip()
        if not raw_dir:
            continue
        configured = Path(raw_dir).expanduser().resolve()
        if workspace_dir == configured or configured in workspace_dir.parents:
            normalized = dict(entry)
            normalized["workspaceDir"] = str(configured)
            candidates.append((len(configured.parts), normalized))
    if candidates:
        candidates.sort(key=lambda item: item[0], reverse=True)
        return candidates[0][1]
    if required:
        raise ValueError(f"当前工作目录没有匹配的开发文档配置：{workspace_dir}。请在 {CONFIG_PATH} 的 workspaces 中新增 workspaceDir。")
    return {}


def _resolve_url(args: argparse.Namespace, config: Mapping[str, Any]) -> str:
    return args.url or os.getenv("PROJECT_DEVELOPMENT_DOCS_URL") or str(config.get("url") or "http://localhost:8080")


def _resolve_project_code(args: argparse.Namespace, config: Mapping[str, Any]) -> str:
    project_code = args.project_code or os.getenv("PROJECT_DEVELOPMENT_DOCS_PROJECT_CODE") or str(config.get("projectCode") or "").strip()
    if not project_code:
        raise ValueError("projectCode not configured")
    return project_code


def _resolve_agent_role(args: argparse.Namespace, config: Mapping[str, Any]) -> str:
    return (args.agent_role or os.getenv("PROJECT_DEVELOPMENT_DOCS_AGENT_ROLE") or str(config.get("agentRole") or "DEVELOPER")).strip().upper()


def _resolve_usage_types(args: argparse.Namespace, config: Mapping[str, Any]) -> tuple[str, ...]:
    role_usage_map = {
        "MAIN": "AGENT_MAIN",
        "DEVELOPER": "AGENT_DEVELOPER",
        "TESTER": "AGENT_TESTER",
        "OPS": "AGENT_OPS",
        "REVIEWER": "AGENT_REVIEWER",
    }
    role_usage = role_usage_map.get(_resolve_agent_role(args, config))
    return ("AGENT_COMMON", role_usage) if role_usage else ("AGENT_COMMON",)


def _usage_title(usage_type: str) -> str:
    return {
        "AGENT_COMMON": "通用文档",
        "AGENT_MAIN": "主控文档",
        "AGENT_DEVELOPER": "开发文档",
        "AGENT_TESTER": "测试文档",
        "AGENT_OPS": "运维文档",
        "AGENT_REVIEWER": "评审文档",
        "GENERAL_DOCUMENT": "通用文档",
        "DEVELOPMENT_DOCUMENT": "开发文档",
    }.get(usage_type, usage_type)


def _fetch_context(args: argparse.Namespace, config: Mapping[str, Any]) -> tuple[str, str]:
    client = DevelopmentDocumentClient(_resolve_url(args, config), timeout=args.timeout)
    if args.context_url:
        project_code = args.project_code or str(config.get("projectCode") or "-").strip() or "-"
        return project_code, client.get_context_url(args.context_url).strip()
    project_code = _resolve_project_code(args, config)
    parts: list[str] = []
    for usage_type in _resolve_usage_types(args, config):
        content = client.get_document_context(project_code, usage_type).strip()
        if content:
            parts.append(f"## {_usage_title(usage_type)}\n\n{content}")
    return project_code, "\n\n".join(parts).strip()


def _handle_config(args: argparse.Namespace) -> dict[str, Any]:
    config = _select_config(args, required=False)
    workspace_dir = _workspace_dir(args)
    return {
        "configPath": str(CONFIG_PATH),
        "config": config,
        "resolved": {
            "url": _resolve_url(args, config),
            "projectCode": _resolve_project_code(args, config) if config or args.project_code else None,
            "agentRole": _resolve_agent_role(args, config),
            "workspaceDir": str(workspace_dir),
            "usageTypes": list(_resolve_usage_types(args, config)),
            "storage": "none",
        },
    }


def _handle_fetch(args: argparse.Namespace) -> dict[str, Any]:
    config = _select_config(args, required=not bool(args.context_url))
    project_code, content = _fetch_context(args, config)
    return {
        "projectCode": project_code,
        "usageTypes": list(_resolve_usage_types(args, config)),
        "contentLength": len(content),
        "loaded": bool(content),
        "storage": "none",
        "content": content,
    }


def _handle_context(args: argparse.Namespace) -> dict[str, Any]:
    return _handle_fetch(args)


def _handle_legacy_no_storage(args: argparse.Namespace) -> dict[str, Any]:
    result = _handle_fetch(args)
    result["legacyCommand"] = args.command
    result["message"] = f"{args.command} no longer writes or reads local files; returned live context instead."
    return result


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Fetch ai-api project development documents as live session context")
    parser.add_argument("--url")
    parser.add_argument("--project-code")
    parser.add_argument("--workspace-dir")
    parser.add_argument("--context-url")
    parser.add_argument("--agent-role")
    parser.add_argument("--timeout", type=float, default=10.0)
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command, handler in {
        "config": _handle_config,
        "fetch": _handle_fetch,
        "context": _handle_context,
        "sync": _handle_legacy_no_storage,
        "load": _handle_legacy_no_storage,
    }.items():
        subparsers.add_parser(command).set_defaults(handler=handler)
    return parser


def _normalize_global_args(argv: list[str] | None) -> list[str]:
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


def _parse_json(raw: str) -> Any:
    if not raw.strip():
        return {}
    return json.loads(raw)


def _to_int(value: Any) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(_normalize_global_args(argv))
    try:
        result = args.handler(args)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except Exception as exc:
        print(json.dumps({"error": type(exc).__name__, "message": str(exc)}, ensure_ascii=False), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
