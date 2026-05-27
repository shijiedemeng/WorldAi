from __future__ import annotations

try:
    from .demo_runner import run_acp_demo
except ImportError:
    from demo_runner import run_acp_demo


def main() -> int:
    from ai_api_client.cli_tools.acp import CodexAcpClient

    return run_acp_demo(
        client_factory=CodexAcpClient,
        default_command="codex app-server --listen stdio://",
        title="Codex App Server",
    )


if __name__ == "__main__":
    raise SystemExit(main())
