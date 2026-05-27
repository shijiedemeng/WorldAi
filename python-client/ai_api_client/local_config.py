from __future__ import annotations

from dataclasses import asdict, dataclass, field
import json
import platform
from pathlib import Path
import socket
from typing import Any


DEFAULT_CONFIG_PATH = Path(__file__).resolve().parents[1] / "client-config.json"


@dataclass
class ServerConfig:
    host: str = "127.0.0.1"
    port: int = 8080
    protocol: str = "http"
    api_base: str = ""

    @classmethod
    def from_dict(cls, payload: dict[str, Any]) -> "ServerConfig":
        return cls(
            host=str(payload.get("host") or "127.0.0.1").strip(),
            port=_parse_port(payload.get("port"), 8080),
            protocol=str(payload.get("protocol") or "http").strip() or "http",
            api_base=str(payload.get("api_base") or payload.get("apiBase") or "").strip(),
        )

    def to_base_url(self) -> str:
        api_base = self.api_base.strip("/")
        url = f"{self.protocol}://{self.host}:{self.port}"
        return f"{url}/{api_base}" if api_base else url


@dataclass
class ControllableAgentConfig:
    agent_code: str
    enabled: bool = True
    workspace_dir: str = ""
    worker_command: str = ""
    skills_dir: str = ""

    @classmethod
    def from_dict(cls, payload: dict[str, Any]) -> "ControllableAgentConfig":
        return cls(
            agent_code=str(payload.get("agent_code") or payload.get("agentCode") or "").strip(),
            enabled=bool(payload.get("enabled", payload.get("enabledFlag", True))),
            workspace_dir=str(payload.get("workspace_dir") or payload.get("workspaceDir") or "").strip(),
            worker_command=str(payload.get("worker_command") or payload.get("workerCommand") or "").strip(),
            skills_dir=str(payload.get("skills_dir") or payload.get("skillsDir") or "").strip(),
        )

    def to_api_payload(self) -> dict[str, Any]:
        return {
            "agentCode": self.agent_code,
            "enabledFlag": self.enabled,
            "workspaceDir": self.workspace_dir or None,
            "workerCommand": self.worker_command or None,
            "skillsDir": self.skills_dir or None,
        }


@dataclass
class McpConfig:
    enabled: bool = False
    server_url: str = ""
    max_file_bytes: int = 200000
    max_entries: int = 200

    @classmethod
    def from_dict(cls, payload: dict[str, Any]) -> "McpConfig":
        return cls(
            enabled=bool(payload.get("enabled", False)),
            server_url=str(payload.get("server_url") or payload.get("serverUrl") or "").strip(),
            max_file_bytes=_parse_positive_int(payload.get("max_file_bytes") or payload.get("maxFileBytes"), 200000),
            max_entries=_parse_positive_int(payload.get("max_entries") or payload.get("maxEntries"), 200),
        )


@dataclass
class LocalClientConfig:
    client_code: str
    client_name: str
    server: ServerConfig = field(default_factory=ServerConfig)
    app_version: str = "python-client"
    agents: list[ControllableAgentConfig] = field(default_factory=list)
    mcp: McpConfig = field(default_factory=McpConfig)

    @property
    def base_url(self) -> str:
        return self.server.to_base_url()

    @classmethod
    def default(cls) -> "LocalClientConfig":
        host_name = socket.gethostname() or platform.node() or "local-client"
        return cls(client_code=host_name.replace(".", "-"), client_name=host_name)

    @classmethod
    def from_dict(cls, payload: dict[str, Any]) -> "LocalClientConfig":
        default = cls.default()
        agents = [ControllableAgentConfig.from_dict(item) for item in payload.get("agents", []) if isinstance(item, dict)]
        agents = [item for item in agents if item.agent_code]
        server_payload = payload.get("server") if isinstance(payload.get("server"), dict) else None
        server = ServerConfig.from_dict(server_payload) if server_payload else _server_from_base_url(
            str(payload.get("base_url") or payload.get("baseUrl") or default.base_url).strip()
        )
        return cls(
            client_code=str(payload.get("client_code") or payload.get("clientCode") or default.client_code).strip(),
            client_name=str(payload.get("client_name") or payload.get("clientName") or default.client_name).strip(),
            server=server,
            app_version=str(payload.get("app_version") or payload.get("appVersion") or default.app_version).strip(),
            agents=agents,
            mcp=McpConfig.from_dict(payload.get("mcp") if isinstance(payload.get("mcp"), dict) else {}),
        )

    def to_dict(self) -> dict[str, Any]:
        payload = asdict(self)
        payload["base_url"] = self.base_url
        payload["agents"] = [asdict(agent) for agent in self.agents]
        return payload


def load_config(path: Path | None = None) -> LocalClientConfig:
    config_path = path or DEFAULT_CONFIG_PATH
    if not config_path.exists():
        return LocalClientConfig.default()
    data = json.loads(config_path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        return LocalClientConfig.default()
    return LocalClientConfig.from_dict(data)


def save_config(config: LocalClientConfig, path: Path | None = None) -> Path:
    config_path = path or DEFAULT_CONFIG_PATH
    config_path.parent.mkdir(parents=True, exist_ok=True)
    config_path.write_text(json.dumps(config.to_dict(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return config_path


def upsert_agent(config: LocalClientConfig, agent: ControllableAgentConfig) -> None:
    config.agents = [item for item in config.agents if item.agent_code != agent.agent_code]
    config.agents.append(agent)


def remove_agent(config: LocalClientConfig, agent_code: str) -> bool:
    before = len(config.agents)
    config.agents = [item for item in config.agents if item.agent_code != agent_code]
    return len(config.agents) != before


def get_local_machine_info() -> dict[str, str]:
    system = platform.system().upper()
    os_type = "MAC" if system == "DARWIN" else "WINDOWS" if system == "WINDOWS" else "LINUX"
    host_name = socket.gethostname() or platform.node() or ""
    return {
        "osType": os_type,
        "hostName": host_name,
        "ipAddress": _best_effort_ip(),
    }


def _best_effort_ip() -> str:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("8.8.8.8", 80))
            return sock.getsockname()[0]
    except OSError:
        try:
            return socket.gethostbyname(socket.gethostname())
        except OSError:
            return ""


def _parse_port(value: Any, default: int) -> int:
    try:
        port = int(value)
    except (TypeError, ValueError):
        return default
    return port if 1 <= port <= 65535 else default


def _parse_positive_int(value: Any, default: int) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return default
    return parsed if parsed > 0 else default


def _server_from_base_url(base_url: str) -> ServerConfig:
    if not base_url:
        return ServerConfig()
    from urllib.parse import urlparse

    parsed = urlparse(base_url)
    if not parsed.scheme or not parsed.hostname:
        return ServerConfig()
    return ServerConfig(
        host=parsed.hostname,
        port=parsed.port or (443 if parsed.scheme == "https" else 80),
        protocol=parsed.scheme,
        api_base=parsed.path.strip("/"),
    )
