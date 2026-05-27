from __future__ import annotations

from dataclasses import asdict, dataclass
import shutil
import subprocess
from typing import Any, Protocol, Sequence


@dataclass(frozen=True)
class DiscoveredRuntime:
    runtime_name: str
    runtime_type: str
    command: str
    available: bool
    version: str
    capability_tags: list[str]

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class CliToolSpec:
    runtime_name: str
    runtime_type: str
    commands: tuple[str, ...]
    version_args: tuple[str, ...]
    capability_tags: tuple[str, ...]


class CliToolProbe(Protocol):
    def discover(self, timeout: float = 3.0) -> DiscoveredRuntime:
        """Return the local runtime status for one CLI tool."""


class StaticCliToolProbe:
    def __init__(self, spec: CliToolSpec) -> None:
        self.spec = spec

    def discover(self, timeout: float = 3.0) -> DiscoveredRuntime:
        executable = find_executable(self.spec.commands)
        available = executable is not None
        version = read_version(executable, self.spec.version_args, timeout) if executable else ""
        return DiscoveredRuntime(
            runtime_name=self.spec.runtime_name,
            runtime_type=self.spec.runtime_type,
            command=executable or self.spec.commands[0],
            available=available,
            version=version,
            capability_tags=list(self.spec.capability_tags),
        )


def find_executable(commands: Sequence[str]) -> str | None:
    for command in commands:
        resolved = shutil.which(command)
        if resolved:
            return resolved
    return None


def read_version(executable: str, version_args: Sequence[str], timeout: float) -> str:
    try:
        completed = subprocess.run(
            [executable, *version_args],
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
    except OSError:
        return ""
    except subprocess.TimeoutExpired:
        return "timeout"

    output = (completed.stdout or completed.stderr or "").strip()
    if not output:
        return ""
    return output.splitlines()[0][:200]
