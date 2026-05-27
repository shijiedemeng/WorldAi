from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime
import json
from pathlib import Path
from typing import Any, Mapping
from urllib import error, parse, request


@dataclass
class AiApiError(Exception):
    message: str
    http_status: int | None = None
    api_code: int | None = None
    response_body: str = ""

    def __str__(self) -> str:
        parts = [self.message]
        if self.http_status is not None:
            parts.append(f"http={self.http_status}")
        if self.api_code is not None:
            parts.append(f"api={self.api_code}")
        return " ".join(parts)


def _to_jsonable(value: Any) -> Any:
    if isinstance(value, datetime):
        return value.isoformat(timespec="seconds")
    if isinstance(value, date):
        return value.isoformat()
    if isinstance(value, Path):
        return str(value)
    if isinstance(value, Mapping):
        return {key: _to_jsonable(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_to_jsonable(item) for item in value]
    return value


class AiApiClient:
    def __init__(self, base_url: str, timeout: float = 10.0) -> None:
        normalized = base_url.rstrip("/")
        self.api_base = normalized if normalized.endswith("/api") else f"{normalized}/api"
        self.timeout = timeout

    def get_agent(self, agent_code: str) -> dict[str, Any]:
        return self._request("GET", f"/agents/{parse.quote(agent_code)}")

    def list_skills(self) -> list[dict[str, Any]]:
        data = self._request("GET", "/skills")
        return data if isinstance(data, list) else []

    def get_skill(self, skill_code: str) -> dict[str, Any]:
        return self._request("GET", f"/skills/{parse.quote(skill_code)}")

    def create_agent(self, payload: Mapping[str, Any]) -> dict[str, Any]:
        return self._request("POST", "/agents", payload)

    def ensure_agent(self, payload: Mapping[str, Any]) -> dict[str, Any]:
        agent_code = str(payload["agentCode"])
        try:
            return self.get_agent(agent_code)
        except AiApiError as exc:
            if exc.http_status != 404:
                raise
        try:
            return self.create_agent(payload)
        except AiApiError as exc:
            if exc.http_status == 400 and exc.api_code == 400 and "already exists" in exc.message:
                return self.get_agent(agent_code)
            raise

    def heartbeat(self, agent_code: str) -> dict[str, Any]:
        return self._request("POST", f"/agents/{parse.quote(agent_code)}/heartbeat")

    def list_tasks(self, agent_code: str) -> list[dict[str, Any]]:
        data = self._request("GET", f"/agents/{parse.quote(agent_code)}/tasks")
        return data if isinstance(data, list) else []

    def update_link_progress(self, link_id: int | str, payload: Mapping[str, Any]) -> dict[str, Any]:
        return self._request("PUT", f"/links/{link_id}/progress", payload)

    def register_client_node(self, payload: Mapping[str, Any]) -> dict[str, Any]:
        return self._request("POST", "/clients/register", payload)

    def heartbeat_client_node(self, client_code: str) -> dict[str, Any]:
        return self._request("POST", f"/clients/{parse.quote(client_code)}/heartbeat")

    def offline_client_node(self, client_code: str) -> None:
        self._request("POST", f"/clients/{parse.quote(client_code)}/offline")

    def list_client_nodes(self) -> list[dict[str, Any]]:
        data = self._request("GET", "/clients")
        return data if isinstance(data, list) else []

    def list_pending_client_commands(self, client_code: str) -> list[dict[str, Any]]:
        data = self._request("GET", f"/clients/{parse.quote(client_code)}/commands/pending")
        return data if isinstance(data, list) else []

    def list_pending_client_session_creations(self, client_code: str) -> list[dict[str, Any]]:
        data = self._request("GET", f"/clients/{parse.quote(client_code)}/sessions/pending-create")
        return data if isinstance(data, list) else []

    def complete_client_session_creation(self, client_code: str, request_id: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        return self._request("POST", f"/clients/{parse.quote(client_code)}/sessions/{parse.quote(request_id)}/complete", payload)

    def append_client_session_event(self, client_code: str, session_id: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        return self._request("POST", f"/clients/{parse.quote(client_code)}/sessions/{parse.quote(session_id)}/events", payload)

    def start_client_command(self, client_code: str, command_id: str) -> dict[str, Any]:
        return self._request("POST", f"/clients/{parse.quote(client_code)}/commands/{parse.quote(command_id)}/start")

    def complete_client_command(self, client_code: str, command_id: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        return self._request("POST", f"/clients/{parse.quote(client_code)}/commands/{parse.quote(command_id)}/complete", payload)

    def complete_mcp_file_request(self, request_id: str, payload: Mapping[str, Any]) -> None:
        self._request("POST", f"/mcp/client-requests/{parse.quote(request_id)}/complete", payload)

    def get_project_markdown_config(self, project_code: str) -> dict[str, Any]:
        return self._request("GET", f"/projects/{parse.quote(project_code)}/markdown-config")

    def get_project_document_context(self, project_code: str, usage_type: str) -> str:
        data = self._request("GET", f"/projects/{parse.quote(project_code)}/document-context/{parse.quote(usage_type)}")
        return data if isinstance(data, str) else ""

    def upload_project_base_markdown(self, project_code: str, payload: Mapping[str, Any]) -> dict[str, Any]:
        return self._request("POST", f"/projects/{parse.quote(project_code)}/markdown-files/base-upload", payload)

    def get_requirement(self, requirement_no: str) -> dict[str, Any]:
        return self._request("GET", f"/requirements/{parse.quote(requirement_no)}")

    def _request(
        self,
        method: str,
        path: str,
        payload: Mapping[str, Any] | None = None,
        query: Mapping[str, Any] | None = None,
    ) -> Any:
        url = f"{self.api_base}{path if path.startswith('/') else '/' + path}"
        if query:
            items = [(key, str(value)) for key, value in query.items() if value is not None]
            if items:
                url = f"{url}?{parse.urlencode(items)}"

        headers = {"Accept": "application/json"}
        body: bytes | None = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            body = json.dumps(_to_jsonable(payload), ensure_ascii=False).encode("utf-8")

        req = request.Request(url, data=body, headers=headers, method=method.upper())
        try:
            with request.urlopen(req, timeout=self.timeout) as response:
                raw = response.read().decode("utf-8")
        except error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            parsed = self._parse_json(raw)
            if isinstance(parsed, dict):
                raise AiApiError(
                    message=str(parsed.get("message", raw or "request failed")),
                    http_status=exc.code,
                    api_code=self._parse_int(parsed.get("code")),
                    response_body=raw,
                ) from exc
            raise AiApiError(message=raw or str(exc), http_status=exc.code, response_body=raw) from exc
        except error.URLError as exc:
            raise AiApiError(message=f"network error: {exc.reason}") from exc

        parsed = self._parse_json(raw)
        if not isinstance(parsed, dict):
            raise AiApiError(message="invalid JSON response", response_body=raw)
        api_code = self._parse_int(parsed.get("code"))
        if api_code != 0:
            raise AiApiError(
                message=str(parsed.get("message", "application error")),
                api_code=api_code,
                response_body=raw,
            )
        return parsed.get("data")

    @staticmethod
    def _parse_json(raw: str) -> Any:
        if not raw.strip():
            return {}
        return json.loads(raw)

    @staticmethod
    def _parse_int(value: Any) -> int | None:
        try:
            return int(value)
        except (TypeError, ValueError):
            return None
