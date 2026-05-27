from __future__ import annotations

try:
    from .demo_runner import create_qoder_session, run_acp_demo
except ImportError:
    from demo_runner import create_qoder_session, run_acp_demo


def main() -> int:
    from ai_api_client.cli_tools.acp import QoderAcpClient

    return run_acp_demo(
        client_factory=QoderAcpClient,
        default_command="qodercli --acp",
        title="Qoder CLI",
        create_session_fn=create_qoder_session,
    )


if __name__ == "__main__":
    raise SystemExit(main())
