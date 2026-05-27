from __future__ import annotations

from typing import Any

from .cli_tools import discover_cli_runtimes


def discover_runtimes(timeout: float = 3.0) -> list[dict[str, Any]]:
    return discover_cli_runtimes(timeout)
