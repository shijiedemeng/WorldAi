from __future__ import annotations

from dataclasses import asdict, dataclass, field
import hashlib
import json
from pathlib import Path
from typing import Any


class MarkdownSyncError(Exception):
    """Base error for workspace Markdown synchronization."""


class MarkdownSyncPathError(MarkdownSyncError):
    """Raised when a server path is not safe to materialize locally."""


class MarkdownSyncConflict(MarkdownSyncError):
    def __init__(self, report: "MarkdownSyncReport") -> None:
        self.report = report
        conflicts = ", ".join(report.conflict_files) or "unknown"
        super().__init__(f"markdown sync conflict: {conflicts}")


@dataclass
class MarkdownSyncReport:
    project_code: str
    markdown_sync_mode: str
    base_markdown_sync_mode: str = "INDEPENDENT"
    base_markdown_allow_client_upload: bool = False
    written_files: list[str] = field(default_factory=list)
    uploaded_files: list[str] = field(default_factory=list)
    skipped_files: list[str] = field(default_factory=list)
    conflict_files: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def sync_project_markdowns(
    api_client: Any,
    project_code: str,
    workspace_dir: str | Path,
    *,
    client_code: str | None = None,
    agent_code: str | None = None,
) -> MarkdownSyncReport:
    workspace = Path(workspace_dir).expanduser().resolve()
    config = api_client.get_project_markdown_config(project_code)
    sync_mode = str(config.get("markdownSyncMode") or "CANCEL")
    base_sync_mode = str(config.get("baseMarkdownSyncMode") or "INDEPENDENT")
    allow_client_upload = bool(config.get("baseMarkdownAllowClientUpload"))
    report = MarkdownSyncReport(
        project_code=project_code,
        markdown_sync_mode=sync_mode,
        base_markdown_sync_mode=base_sync_mode,
        base_markdown_allow_client_upload=allow_client_upload,
    )
    metadata = _load_version_metadata(workspace)

    for file_info in config.get("markdownFiles") or []:
        relative_path = str(file_info.get("filePath") or "").strip()
        content = str(file_info.get("content") or "")
        if not relative_path or not content.strip():
            continue

        file_type = str(file_info.get("fileType") or "").upper()
        is_base_file = file_type == "BASE"
        if is_base_file and base_sync_mode != "AUTO_UPDATE":
            report.skipped_files.append(relative_path)
            continue

        local_path = _resolve_workspace_path(workspace, relative_path)
        server_version = _parse_version(file_info.get("versionNo"))
        metadata_key = _metadata_key(project_code, relative_path)
        local_version = _metadata_version(metadata, metadata_key)
        if local_path.exists():
            if local_path.is_dir():
                raise MarkdownSyncPathError(f"target path is a directory: {relative_path}")
            existing = local_path.read_text(encoding="utf-8")
            if existing == content:
                _set_metadata_version(metadata, metadata_key, project_code, relative_path, server_version, content)
                report.skipped_files.append(relative_path)
                continue
            if is_base_file:
                metadata_hash = _metadata_hash(metadata, metadata_key)
                local_modified = not metadata_hash or _content_hash(existing) != metadata_hash
                upload_version = local_version
                if local_modified:
                    upload_version = max(local_version, server_version) + 1
                if upload_version > server_version:
                    if not allow_client_upload:
                        report.conflict_files.append(relative_path)
                        continue
                    uploaded = api_client.upload_project_base_markdown(
                        project_code,
                        {
                            "agentRole": file_info.get("agentRole"),
                            "baseKey": file_info.get("baseKey"),
                            "filePath": relative_path,
                            "content": existing,
                            "versionNo": upload_version,
                            "clientCode": client_code,
                            "agentCode": agent_code,
                        },
                    )
                    uploaded_version = _parse_version(uploaded.get("versionNo") if isinstance(uploaded, dict) else upload_version)
                    _set_metadata_version(metadata, metadata_key, project_code, relative_path, uploaded_version, existing)
                    report.uploaded_files.append(relative_path)
                    continue
            if sync_mode == "CANCEL":
                report.conflict_files.append(relative_path)
                continue

        local_path.parent.mkdir(parents=True, exist_ok=True)
        local_path.write_text(content, encoding="utf-8")
        _set_metadata_version(metadata, metadata_key, project_code, relative_path, server_version, content)
        report.written_files.append(relative_path)

    _save_version_metadata(workspace, metadata)
    if report.conflict_files:
        raise MarkdownSyncConflict(report)
    return report


def _resolve_workspace_path(workspace: Path, relative_path: str) -> Path:
    if relative_path.startswith("/") or relative_path.startswith("./"):
        raise MarkdownSyncPathError(f"markdown path must be relative: {relative_path}")
    candidate = (workspace / relative_path).resolve()
    if candidate != workspace and workspace not in candidate.parents:
        raise MarkdownSyncPathError(f"markdown path escapes workspace: {relative_path}")
    return candidate


def _metadata_path(workspace: Path) -> Path:
    return workspace / ".ai-api-md-versions.json"


def _load_version_metadata(workspace: Path) -> dict[str, Any]:
    path = _metadata_path(workspace)
    if not path.exists():
        return {"files": {}}
    try:
        parsed = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {"files": {}}
    if not isinstance(parsed, dict):
        return {"files": {}}
    files = parsed.get("files")
    if not isinstance(files, dict):
        parsed["files"] = {}
    return parsed


def _save_version_metadata(workspace: Path, metadata: dict[str, Any]) -> None:
    path = _metadata_path(workspace)
    path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _metadata_key(project_code: str, relative_path: str) -> str:
    return f"{project_code}:{relative_path}"


def _metadata_version(metadata: dict[str, Any], key: str) -> int:
    files = metadata.get("files")
    if not isinstance(files, dict):
        return 0
    entry = files.get(key)
    if not isinstance(entry, dict):
        return 0
    return _parse_version(entry.get("versionNo"), default=0)


def _metadata_hash(metadata: dict[str, Any], key: str) -> str:
    files = metadata.get("files")
    if not isinstance(files, dict):
        return ""
    entry = files.get(key)
    if not isinstance(entry, dict):
        return ""
    return str(entry.get("contentHash") or "")


def _set_metadata_version(
    metadata: dict[str, Any],
    key: str,
    project_code: str,
    relative_path: str,
    version_no: int,
    content: str,
) -> None:
    files = metadata.setdefault("files", {})
    if not isinstance(files, dict):
        metadata["files"] = {}
        files = metadata["files"]
    files[key] = {
        "projectCode": project_code,
        "filePath": relative_path,
        "versionNo": max(1, version_no),
        "contentHash": _content_hash(content),
    }


def _parse_version(value: Any, default: int = 1) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return default
    return parsed if parsed >= 1 else default


def _content_hash(content: str) -> str:
    return hashlib.sha256(content.encode("utf-8")).hexdigest()
