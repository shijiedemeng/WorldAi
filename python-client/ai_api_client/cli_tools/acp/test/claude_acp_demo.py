from __future__ import annotations

try:
    from .demo_runner import run_acp_demo
except ImportError:
    from demo_runner import run_acp_demo


def main() -> int:
    from ai_api_client.cli_tools.acp import ClaudeAcpClient

    return run_acp_demo(
        client_factory=ClaudeAcpClient,
        default_command="claude --acp",
        title="Claude Code",
    )


if __name__ == "__main__":
    raise SystemExit(main())
