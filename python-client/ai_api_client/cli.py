from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

from .agent_discovery import discover_runtimes
from .api import AiApiClient, AiApiError
from .command_runner import ClientCommandRunner
from .markdown_sync import MarkdownSyncConflict, sync_project_markdowns
from .websocket_client import ClientDispatchWebSocket
from .local_config import (
    ControllableAgentConfig,
    DEFAULT_CONFIG_PATH,
    LocalClientConfig,
    get_local_machine_info,
    load_config,
    save_config,
    upsert_agent,
)
from .scheduler import AgentScheduler, SchedulerConfig


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Python execution client for the local ai-api backend")
    parser.add_argument("--base-url")
    parser.add_argument("--agent-code")
    parser.add_argument("--agent-name")
    parser.add_argument("--agent-role", default="DEVELOPER")
    parser.add_argument("--agent-desc", default="")
    parser.add_argument("--supported-link-types", default="")
    parser.add_argument("--capability-tags", default="")
    parser.add_argument("--callback-mode", default="PULL")
    parser.add_argument("--endpoint-url", default="")
    parser.add_argument("--status", default="ONLINE")
    parser.add_argument("--project-code")
    parser.add_argument("--workspace-dir")
    parser.add_argument("--skills-dir")
    parser.add_argument("--sync-markdowns", action="store_true")
    parser.add_argument("--sync-only", action="store_true")
    parser.add_argument("--poll-interval", type=float, default=15.0)
    parser.add_argument("--heartbeat-interval", type=float, default=30.0)
    parser.add_argument("--worker-command")
    parser.add_argument("--worker-timeout", type=float)
    parser.add_argument("--once", action="store_true")
    parser.add_argument("--print-runtimes", action="store_true")
    parser.add_argument("--client-config", type=Path, default=DEFAULT_CONFIG_PATH)
    parser.add_argument("--init-client-config", action="store_true")
    parser.add_argument("--add-controllable-agent", action="store_true")
    parser.add_argument("--list-controllable-agents", action="store_true")
    parser.add_argument("--show-client-config", action="store_true")
    parser.add_argument("--register-client-node", action="store_true")
    parser.add_argument("--use-client-config", action="store_true")
    parser.add_argument("--client-code")
    parser.add_argument("--client-name")
    parser.add_argument("--app-version")
    parser.add_argument("--enabled", action=argparse.BooleanOptionalAction, default=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    raw_argv = list(sys.argv[1:] if argv is None else argv)
    args = parser.parse_args(argv)
    if not raw_argv:
        args.use_client_config = True

    if args.print_runtimes:
        print(json.dumps(discover_runtimes(), ensure_ascii=False, indent=2))
        if not args.agent_code and not args.agent_name:
            return 0

    if args.init_client_config:
        config = load_config(args.client_config)
        if args.client_code:
            config.client_code = args.client_code.strip()
        if args.client_name:
            config.client_name = args.client_name.strip()
        if args.base_url:
            config.server = LocalClientConfig.from_dict({"base_url": args.base_url.strip()}).server
        if args.app_version:
            config.app_version = args.app_version.strip()
        path = save_config(config, args.client_config)
        print(f"client config saved: {path}")
        return 0

    if args.add_controllable_agent:
        if not args.agent_code:
            parser.error("--agent-code is required with --add-controllable-agent")
        config = load_config(args.client_config)
        upsert_agent(
            config,
            ControllableAgentConfig(
                agent_code=args.agent_code,
                enabled=args.enabled,
                workspace_dir=args.workspace_dir or "",
                worker_command=args.worker_command or "",
                skills_dir=args.skills_dir or "",
            ),
        )
        path = save_config(config, args.client_config)
        print(f"controllable agent saved to {path}: {args.agent_code}")
        return 0

    if args.list_controllable_agents:
        config = load_config(args.client_config)
        print(json.dumps(config.to_dict(), ensure_ascii=False, indent=2))
        return 0

    if args.show_client_config:
        config = load_config(args.client_config)
        payload = config.to_dict()
        payload["configPath"] = str(args.client_config.expanduser().resolve())
        payload["templatePath"] = str(args.client_config.expanduser().with_name("client-config.example.json").resolve())
        payload["说明"] = "直接启动默认读取 configPath；client-config.example.json 只是模板，不会自动生效。"
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 0

    if args.use_client_config or args.register_client_node:
        config_path = args.client_config.expanduser().resolve()
        config = load_config(args.client_config)
        if args.client_code:
            config.client_code = args.client_code.strip()
        if args.client_name:
            config.client_name = args.client_name.strip()
        if args.base_url:
            config.server = LocalClientConfig.from_dict({"base_url": args.base_url.strip()}).server
        if args.app_version:
            config.app_version = args.app_version.strip()
        print_client_config_summary(config_path, config)
        client = AiApiClient(config.base_url)
        try:
            registered = register_client_from_config(client, config)
        except AiApiError as exc:
            print(f"客户端启动失败：无法注册到服务端 {config.base_url}。请确认后端地址和端口已启动。错误：{exc}", file=sys.stderr)
            return 1
        print_registration_summary(config, registered)
        sync_registered_agent_markdowns(client, config, registered)
        print(json.dumps(registered, ensure_ascii=False, indent=2))
        if args.register_client_node:
            return 0
        if not args.agent_code and not args.agent_name:
            return run_client_config_heartbeat_loop(client, config, args.heartbeat_interval)

    if not args.agent_code or not args.agent_name:
        parser.error("--agent-code and --agent-name are required")

    sync_markdowns = args.sync_markdowns or args.sync_only
    workspace_dir = Path(args.workspace_dir).expanduser().resolve() if args.workspace_dir else None
    if sync_markdowns and workspace_dir is None:
        parser.error("--workspace-dir is required when markdown sync is enabled")

    scheduler = AgentScheduler(
        SchedulerConfig(
            base_url=args.base_url or "http://localhost:8080",
            agent_code=args.agent_code,
            agent_name=args.agent_name,
            agent_role=args.agent_role,
            agent_desc=args.agent_desc,
            supported_link_types=args.supported_link_types,
            capability_tags=args.capability_tags,
            callback_mode=args.callback_mode,
            endpoint_url=args.endpoint_url,
            status=args.status,
            project_code=args.project_code,
            workspace_dir=workspace_dir,
            sync_markdowns=sync_markdowns,
            sync_only=args.sync_only,
            poll_interval=args.poll_interval,
            heartbeat_interval=args.heartbeat_interval,
            worker_command=args.worker_command,
            worker_timeout=args.worker_timeout,
            once=args.once,
        )
    )
    scheduler.run()
    return 0


def register_client_from_config(client: AiApiClient, config: LocalClientConfig) -> dict:
    machine_info = get_local_machine_info()
    runtimes = [runtime_to_api_payload(item) for item in discover_runtimes()]
    payload = {
        "clientCode": config.client_code,
        "clientName": config.client_name,
        "osType": machine_info["osType"],
        "hostName": machine_info["hostName"],
        "ipAddress": machine_info["ipAddress"],
        "connectionProtocol": "WEBSOCKET",
        "status": "ONLINE",
        "supportedAgentTypes": ",".join(runtime["agentType"] for runtime in runtimes if runtime.get("availableFlag")),
        "appVersion": config.app_version,
        "mcpEnabled": bool(config.mcp.enabled),
        "mcpServerUrl": config.mcp.server_url or f"{config.base_url.rstrip('/')}/api/mcp",
        "runtimes": runtimes,
        "agents": [agent.to_api_payload() for agent in config.agents],
    }
    return client.register_client_node(payload)


def print_client_config_summary(config_path: Path, config: LocalClientConfig) -> None:
    template_path = config_path.with_name("client-config.example.json")
    configured_agents = [agent.agent_code for agent in config.agents]
    print(f"读取客户端配置：{config_path}", file=sys.stderr)
    print(f"服务端地址：{config.base_url}", file=sys.stderr)
    print(f"MCP 文件检索：{'开启' if config.mcp.enabled else '关闭'}", file=sys.stderr)
    print(f"配置 Agent：{format_codes(configured_agents)}", file=sys.stderr)
    for agent in config.agents:
        print(
            f"Agent {agent.agent_code} 技能目录：{agent.skills_dir or (Path(agent.workspace_dir).expanduser() / 'skills')}",
            file=sys.stderr,
        )
    if template_path.exists() and template_path != config_path:
        print(f"模板文件仅作参考，不会自动读取：{template_path}", file=sys.stderr)


def print_registration_summary(config: LocalClientConfig, registered: dict) -> None:
    configured_agents = {agent.agent_code for agent in config.agents}
    linked_agents = {
        str(agent.get("agentCode")).strip()
        for agent in registered.get("agents", [])
        if isinstance(agent, dict) and agent.get("agentCode")
    }
    unlinked_agents = {
        str(agent.get("agentCode")).strip()
        for agent in registered.get("unlinkedAgents", [])
        if isinstance(agent, dict) and agent.get("agentCode")
    }
    unlinked_agents.update(configured_agents - linked_agents - unlinked_agents)
    print(f"服务端实际关联 Agent：{format_codes(sorted(linked_agents))}", file=sys.stderr)
    if unlinked_agents:
        print(
            f"未关联 Agent：{format_codes(sorted(unlinked_agents))}。请确认这些编号已在页面的 Agent 管理中创建。",
            file=sys.stderr,
        )


def sync_registered_agent_markdowns(client: AiApiClient, config: LocalClientConfig, registered: dict) -> None:
    agent_config_map = {agent.agent_code: agent for agent in config.agents if agent.enabled}
    synced: set[tuple[str, str]] = set()
    for linked_agent in registered.get("agents", []) or []:
        if not isinstance(linked_agent, dict):
            continue
        agent_code = str(linked_agent.get("agentCode") or "").strip()
        project_code = str(linked_agent.get("projectCode") or "").strip()
        if not agent_code or not project_code:
            continue
        agent_config = agent_config_map.get(agent_code)
        if not agent_config or not agent_config.workspace_dir:
            continue
        sync_key = (project_code, str(Path(agent_config.workspace_dir).expanduser().resolve()))
        if sync_key in synced:
            continue
        synced.add(sync_key)
        try:
            report = sync_project_markdowns(
                client,
                project_code,
                agent_config.workspace_dir,
                client_code=config.client_code,
                agent_code=agent_code,
            )
            print(
                f"基础 MD 同步：project={project_code} written={len(report.written_files)} "
                f"uploaded={len(report.uploaded_files)} skipped={len(report.skipped_files)}",
                file=sys.stderr,
            )
        except MarkdownSyncConflict as exc:
            print(
                f"基础 MD 同步存在冲突：project={project_code} files={format_codes(exc.report.conflict_files)}",
                file=sys.stderr,
            )
        except Exception as exc:
            print(f"基础 MD 同步失败：project={project_code} {type(exc).__name__}: {exc}", file=sys.stderr)


def format_codes(codes: list[str] | set[str]) -> str:
    normalized = [code for code in codes if code]
    return "、".join(normalized) if normalized else "无"


def runtime_to_api_payload(runtime: dict) -> dict:
    return {
        "agentType": runtime.get("runtime_type"),
        "agentName": runtime.get("runtime_name"),
        "agentVersion": runtime.get("version") or None,
        "commandPath": runtime.get("command") or None,
        "availableFlag": bool(runtime.get("available")),
        "capabilityTags": ",".join(runtime.get("capability_tags") or []) or None,
        "supportsSessionReuse": True,
        "defaultWorkspaceDir": None,
    }


def run_client_config_heartbeat_loop(client: AiApiClient, config: LocalClientConfig, heartbeat_interval: float) -> int:
    interval = max(1.0, heartbeat_interval)
    command_runner = ClientCommandRunner(client, config)
    websocket = ClientDispatchWebSocket(
        config.base_url,
        config.client_code,
        command_runner.run_command,
        command_runner.run_session_create,
        command_runner.run_mcp_file_request,
    )
    websocket.start()
    try:
        while True:
            import time

            try:
                command_runner.run_pending_once()
            except AiApiError as exc:
                if exc.http_status == 404 or exc.api_code == 404:
                    try:
                        registered = register_client_from_config(client, config)
                        print_registration_summary(config, registered)
                        sync_registered_agent_markdowns(client, config, registered)
                        print(f"客户端已重新注册：{config.client_code}", file=sys.stderr)
                    except AiApiError as register_exc:
                        print(f"客户端重新注册失败，将继续重试：{register_exc}", file=sys.stderr)
                    time.sleep(interval)
                    continue
                print(f"客户端拉取执行命令失败，将继续重试：{exc}", file=sys.stderr)
            except Exception as exc:
                print(f"客户端执行命令失败，将继续重试：{type(exc).__name__}: {exc}", file=sys.stderr)
            time.sleep(interval)
    except KeyboardInterrupt:
        websocket.stop()
        try:
            client.offline_client_node(config.client_code)
            print(f"client offline sent: {config.client_code}")
        except AiApiError as exc:
            print(f"客户端下线通知失败，进程直接退出：{exc}", file=sys.stderr)
        return 0
