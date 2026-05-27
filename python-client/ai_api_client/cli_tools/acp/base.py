from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Protocol


@dataclass(frozen=True)
class AcpSession:
    session_id: str
    cwd: str
    default: bool = False


@dataclass(frozen=True)
class AcpPromptResult:
    text: str
    thought: str = ""
    raw: dict | None = None


class AcpClient(Protocol):
    def initialize(self) -> dict | None:
        """Initialize the ACP client once."""

    def list_sessions(self) -> dict | None:
        """Return runtime session list."""

    def create_session(self, cwd: str | Path) -> AcpSession:
        """Create a new ACP session."""

    def prompt(self, session_id: str, text: str) -> AcpPromptResult:
        """Send a prompt to one ACP session."""
