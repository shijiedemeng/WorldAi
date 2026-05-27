from __future__ import annotations

from .base import CliToolSpec, StaticCliToolProbe


class CodexCliProbe(StaticCliToolProbe):
    def __init__(self) -> None:
        super().__init__(
            CliToolSpec(
                runtime_name="Codex CLI",
                runtime_type="CODEX_CLI",
                commands=("codex",),
                version_args=("--version",),
                capability_tags=("coding", "cli", "openai"),
            )
        )
