from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any, Callable, Optional, Tuple


def ensure_project_path() -> None:
    current = Path(__file__).resolve()
    for parent in current.parents:
        if (parent / "ai_api_client").is_dir():
            sys.path.insert(0, str(parent))
            return


ensure_project_path()

from ai_api_client.cli_tools.acp.base import AcpClient, AcpSession  # noqa: E402


CreateSessionFn = Callable[[AcpClient, Path], Tuple[AcpSession, Optional[dict[str, Any]]]]


def run_acp_demo(
    client_factory: Callable[[str], AcpClient],
    default_command: str,
    title: str,
    create_session_fn: CreateSessionFn | None = None,
    argv: list[str] | None = None,
) -> int:
    parser = argparse.ArgumentParser(description=f"{title} ACP demo")
    parser.add_argument("--command", default=default_command, help="ACP 启动命令")
    parser.add_argument("--cwd", default=str(Path.cwd()), help="创建会话使用的工作目录")
    parser.add_argument("--message", default="你好", help="发送给 ACP 会话的文本")
    args = parser.parse_args(argv)

    client = client_factory(args.command)
    try:
        print_section("初始化")
        print_json(client.initialize())

        print_section("查询会话")
        print_json(client.list_sessions())

        print_section("创建会话")
        session, create_raw = create_session(client, Path(args.cwd), create_session_fn)
        print_json({
            "sessionId": session.session_id,
            "cwd": session.cwd,
            "default": session.default,
            "raw": create_raw,
        })

        print_section(f"发送消息：{args.message}")
        result = client.prompt(session.session_id, args.message)
        print_json({
            "text": result.text,
            "thought": result.thought,
            "raw": result.raw,
        })
        return 0
    finally:
        terminate_client_process(client)


def create_session(
    client: AcpClient,
    cwd: Path,
    create_session_fn: CreateSessionFn | None,
) -> tuple[AcpSession, dict[str, Any] | None]:
    if create_session_fn is not None:
        return create_session_fn(client, cwd)
    session = client.create_session(cwd)
    return session, None


def create_qoder_session(client: AcpClient, cwd: Path) -> tuple[AcpSession, dict[str, Any] | None]:
    call = getattr(client, "call", None)
    if not callable(call):
        session = client.create_session(cwd)
        return session, None
    response = call("session/new", {"cwd": str(cwd), "mcpServers": []}, req_id=3, timeout=30)
    session_id = extract_session_id(response)
    if not session_id:
        print_section("session/new 原始响应")
        print_json(response)
        raise RuntimeError("ACP session/new did not return usable session id")
    return AcpSession(session_id=session_id, cwd=str(cwd), default=True), response


def extract_session_id(value: Any) -> str:
    if isinstance(value, dict):
        for key in ("sessionId", "session_id", "threadId", "id"):
            current = value.get(key)
            if isinstance(current, str) and current.strip():
                return current.strip()
        for key in ("session", "thread", "result", "data"):
            nested = extract_session_id(value.get(key))
            if nested:
                return nested
        for nested_value in value.values():
            nested = extract_session_id(nested_value)
            if nested:
                return nested
    if isinstance(value, list):
        for item in value:
            nested = extract_session_id(item)
            if nested:
                return nested
    return ""


def print_section(title: str) -> None:
    print(f"\n===== {title} =====")


def print_json(value: Any) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2, default=str))


def terminate_client_process(client: AcpClient) -> None:
    proc = getattr(client, "proc", None)
    if proc is None:
        return
    if proc.poll() is not None:
        return
    proc.terminate()
    try:
        proc.wait(timeout=3)
    except Exception:
        proc.kill()
