from __future__ import annotations

from .base import CliToolSpec, StaticCliToolProbe


class QoderCliProbe(StaticCliToolProbe):
    def __init__(self) -> None:
        super().__init__(
            CliToolSpec(
                runtime_name="Qoder CLI",
                runtime_type="QODER_CLI",
                commands=("qodercli", "qoder"),
                version_args=("--version",),
                capability_tags=("coding", "cli", "qoder"),
            )
        )
