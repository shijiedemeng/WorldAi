from __future__ import annotations

from .base import CliToolSpec, StaticCliToolProbe


class ClaudeCodeProbe(StaticCliToolProbe):
    def __init__(self) -> None:
        super().__init__(
            CliToolSpec(
                runtime_name="Claude Code",
                runtime_type="CLAUDE_CODE",
                commands=("claude", "claude-code"),
                version_args=("--version",),
                capability_tags=("coding", "cli", "claude"),
            )
        )
