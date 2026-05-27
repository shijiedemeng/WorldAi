from __future__ import annotations

from pathlib import Path
from typing import Any

from .local_config import ControllableAgentConfig, LocalClientConfig


def handle_mcp_file_request(config: LocalClientConfig, request: dict[str, Any]) -> dict[str, Any]:
    request_id = str(request.get("requestId") or "")
    agent_code = str(request.get("agentCode") or "").strip()
    operation = str(request.get("operation") or "TREE").strip().upper()
    agent = _find_agent(config, agent_code)
    response: dict[str, Any] = {
        "requestId": request_id,
        "agentCode": agent_code,
        "clientCode": config.client_code,
        "enabled": bool(config.mcp.enabled),
        "operation": operation,
        "entries": [],
        "matches": [],
    }
    if not config.mcp.enabled:
        response["errorMessage"] = "client mcp is disabled"
        return response
    if agent is None:
        response["errorMessage"] = f"agent not configured: {agent_code}"
        return response
    try:
        workspace = Path(agent.workspace_dir).expanduser().resolve()
        if not workspace.exists() or not workspace.is_dir():
            raise ValueError(f"workspace_dir not found: {workspace}")
        target = _safe_target(workspace, str(request.get("path") or "."))
        response["basePath"] = str(workspace)
        response["path"] = str(target.relative_to(workspace)) if target != workspace else "."
        if operation == "READ":
            response["content"] = _read_file(target, config.mcp.max_file_bytes)
        elif operation == "LIST":
            response["entries"] = _list_directory(target, workspace, _limit(request.get("limit"), config.mcp.max_entries))
        elif operation == "SEARCH":
            response["matches"] = _search_files(
                target,
                workspace,
                str(request.get("keyword") or "").strip(),
                _depth(request.get("maxDepth"), 5),
                _limit(request.get("limit"), config.mcp.max_entries),
            )
        else:
            response["entries"] = _tree(target, workspace, _depth(request.get("maxDepth"), 3), _limit(request.get("limit"), config.mcp.max_entries))
    except Exception as exc:
        response["errorMessage"] = f"{type(exc).__name__}: {exc}"
    return response


def _find_agent(config: LocalClientConfig, agent_code: str) -> ControllableAgentConfig | None:
    for agent in config.agents:
        if agent.agent_code == agent_code and agent.enabled:
            return agent
    return None


def _safe_target(workspace: Path, requested: str) -> Path:
    raw = Path(requested).expanduser()
    target = raw if raw.is_absolute() else workspace / raw
    resolved = target.resolve()
    if resolved != workspace and workspace not in resolved.parents:
        raise ValueError("path is outside workspace")
    return resolved


def _entry(path: Path, workspace: Path, depth: int = 0, preview: str | None = None) -> dict[str, Any]:
    rel = "." if path == workspace else str(path.relative_to(workspace))
    return {
        "path": rel,
        "name": path.name or str(workspace),
        "type": "directory" if path.is_dir() else "file",
        "size": path.stat().st_size if path.exists() and path.is_file() else None,
        "depth": depth,
        "preview": preview,
    }


def _list_directory(path: Path, workspace: Path, limit: int) -> list[dict[str, Any]]:
    if not path.is_dir():
        raise ValueError("path is not a directory")
    entries = sorted(path.iterdir(), key=lambda item: (not item.is_dir(), item.name.lower()))
    return [_entry(item, workspace) for item in entries if not item.is_symlink()][:limit]


def _tree(path: Path, workspace: Path, max_depth: int, limit: int) -> list[dict[str, Any]]:
    if not path.is_dir():
        raise ValueError("path is not a directory")
    result: list[dict[str, Any]] = []

    def visit(current: Path, depth: int) -> None:
        if len(result) >= limit or depth > max_depth:
            return
        if depth > 0:
            result.append(_entry(current, workspace, depth))
        if current.is_dir() and depth < max_depth:
            for child in sorted(current.iterdir(), key=lambda item: (not item.is_dir(), item.name.lower())):
                if len(result) >= limit:
                    return
                if child.name in {".git", "node_modules", "dist", "target", "__pycache__"}:
                    continue
                if child.is_symlink():
                    continue
                visit(child, depth + 1)

    visit(path, 0)
    return result


def _read_file(path: Path, max_file_bytes: int) -> str:
    if not path.is_file():
        raise ValueError("path is not a file")
    with path.open("rb") as handle:
        data = handle.read(max_file_bytes + 1)
    if len(data) > max_file_bytes:
        data = data[:max_file_bytes]
    return data.decode("utf-8", errors="replace")


def _search_files(path: Path, workspace: Path, keyword: str, max_depth: int, limit: int) -> list[dict[str, Any]]:
    if not keyword:
        raise ValueError("keyword is required")
    root = path if path.is_dir() else path.parent
    result: list[dict[str, Any]] = []
    for item in root.rglob("*"):
        if len(result) >= limit:
            break
        if any(part in {".git", "node_modules", "dist", "target", "__pycache__"} for part in item.parts):
            continue
        if item.is_symlink():
            continue
        try:
            depth = len(item.relative_to(root).parts)
        except ValueError:
            continue
        if depth > max_depth or not item.is_file():
            continue
        if keyword.lower() in item.name.lower():
            result.append(_entry(item, workspace, depth, "filename matched"))
            continue
        try:
            content = _read_file(item, 20000)
        except Exception:
            continue
        index = content.lower().find(keyword.lower())
        if index >= 0:
            preview = content[max(0, index - 80): index + len(keyword) + 80].replace("\n", " ")
            result.append(_entry(item, workspace, depth, preview))
    return result


def _limit(value: Any, default: int) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return default
    return max(1, min(parsed, default))


def _depth(value: Any, default: int) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return default
    return max(0, min(parsed, 20))
