from __future__ import annotations

from typing import Any, Sequence

from .base import CliToolProbe
from .claude import ClaudeCodeProbe
from .codex import CodexCliProbe
from .qoder import QoderCliProbe


DEFAULT_CLI_PROBES: tuple[CliToolProbe, ...] = (
    CodexCliProbe(),
    QoderCliProbe(),
    ClaudeCodeProbe(),
)


def discover_cli_runtimes(timeout: float = 3.0, probes: Sequence[CliToolProbe] | None = None) -> list[dict[str, Any]]:
    active_probes = probes or DEFAULT_CLI_PROBES
    return [probe.discover(timeout).to_dict() for probe in active_probes]
