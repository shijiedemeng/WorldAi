from __future__ import annotations

import json
from pathlib import Path
import shlex
import subprocess
import threading
import time
from typing import Any

from .base import AcpPromptResult, AcpSession


class QoderAcpClient:
    def __init__(self, command: str = "qodercli --acp") -> None:
        self.proc = subprocess.Popen(
            shlex.split(command),
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
        self.response_map: dict[int, dict[str, Any]] = {}
        self.lock = threading.Lock()
        self.stream_buffer = {"thought": "", "text": ""}
        self.stderr_lines: list[str] = []
        self.initialized = False
        threading.Thread(target=self._reader, daemon=True).start()
        threading.Thread(target=self._err_reader, daemon=True).start()

    def _reader(self) -> None:
        assert self.proc.stdout is not None
        for line in self.proc.stdout:
            line = line.strip()
            if not line:
                continue
            try:
                msg = json.loads(line)
            except json.JSONDecodeError:
                continue
            if "id" in msg and ("result" in msg or "error" in msg):
                with self.lock:
                    self.response_map[int(msg["id"])] = msg
            if msg.get("method") == "session/update":
                update = msg.get("params", {}).get("update", {})
                update_type = update.get("sessionUpdate")
                content = update.get("content", {})
                if update_type == "agent_thought_chunk":
                    self.stream_buffer["thought"] += str(content.get("text") or "")
                elif update_type == "agent_message_chunk":
                    self.stream_buffer["text"] += str(content.get("text") or "")

    def _err_reader(self) -> None:
        assert self.proc.stderr is not None
        for line in self.proc.stderr:
            text = line.strip()
            if not text:
                continue
            with self.lock:
                self.stderr_lines.append(text)
                if len(self.stderr_lines) > 20:
                    self.stderr_lines = self.stderr_lines[-20:]

    def call(self, method: str, params: dict[str, Any] | None = None, req_id: int | None = None, timeout: float = 10.0) -> dict[str, Any] | None:
        if req_id is None:
            req_id = int(time.time() * 1000) % 100000
        request = {
            "jsonrpc": "2.0",
            "id": req_id,
            "method": method,
            "params": params or {},
        }
        assert self.proc.stdin is not None
        self.proc.stdin.write(json.dumps(request, ensure_ascii=False) + "\n")
        self.proc.stdin.flush()
        start = time.time()
        while time.time() - start < timeout:
            with self.lock:
                if req_id in self.response_map:
                    return self.response_map.pop(req_id)
            time.sleep(0.05)
        return None

    def initialize(self) -> dict[str, Any] | None:
        if self.initialized:
            return {"alreadyInitialized": True}
        result = self.call(
            "initialize",
            {
                "protocolVersion": 1,
                "clientInfo": {
                    "name": "ai-api-python-client",
                    "version": "1.0",
                },
            },
            req_id=1,
        )
        self.initialized = True
        return result

    def list_sessions(self) -> dict[str, Any] | None:
        return self.call("session/list", {}, req_id=2)

    def create_session(self, cwd: str | Path) -> AcpSession:
        response = self.call("session/new", {"cwd": str(cwd), "mcpServers": []}, req_id=3, timeout=30)
        error = (response or {}).get("error") if isinstance(response, dict) else None
        if error:
            raise RuntimeError(f"ACP session/new failed: {error}")
        session_id = str((response or {}).get("result", {}).get("sessionId") or "")
        if not session_id:
            raise RuntimeError(f"ACP session/new did not return sessionId: {self._stderr_tail()}")
        return AcpSession(session_id=session_id, cwd=str(cwd), default=True)

    def prompt(self, session_id: str, text: str) -> AcpPromptResult:
        self.stream_buffer = {"thought": "", "text": ""}
        response = self.call(
            "session/prompt",
            {
                "sessionId": session_id,
                "prompt": [{"type": "text", "text": text}],
            },
            req_id=4,
            timeout=60,
        )
        return AcpPromptResult(text=self.stream_buffer["text"], thought=self.stream_buffer["thought"], raw=response)

    def _stderr_tail(self) -> str:
        with self.lock:
            return " | ".join(self.stderr_lines[-5:])
