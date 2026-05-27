from __future__ import annotations

import itertools
import json
from pathlib import Path
import shlex
import subprocess
import threading
import time
from typing import Any

from .base import AcpPromptResult, AcpSession


class ClaudeAcpClient:
    def __init__(self, command: str = "claude --acp") -> None:
        self.proc = subprocess.Popen(
            shlex.split(command),
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
        self.response_map: dict[str, dict[str, Any]] = {}
        self.stream_buffers: dict[str, dict[str, str]] = {}
        self.stderr_lines: list[str] = []
        self.lock = threading.Lock()
        self.initialized = False
        self.request_ids = itertools.count(1)
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
            if not isinstance(msg, dict):
                continue
            if "id" in msg and ("result" in msg or "error" in msg):
                with self.lock:
                    self.response_map[str(msg["id"])] = msg
                continue
            if msg.get("method") == "session/update":
                self._handle_session_update(msg)

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

    def _handle_session_update(self, msg: dict[str, Any]) -> None:
        params = msg.get("params")
        if not isinstance(params, dict):
            return
        session_id = str(params.get("sessionId") or params.get("session_id") or "")
        update = params.get("update")
        if not isinstance(update, dict):
            return
        target_key = session_id or "_default"
        update_type = str(update.get("sessionUpdate") or update.get("type") or update.get("kind") or "")
        content = update.get("content")
        text = self._extract_text(content if content is not None else update)
        if not text:
            return
        with self.lock:
            buffer = self.stream_buffers.setdefault(target_key, {"thought": "", "text": ""})
            normalized = update_type.replace("-", "_").lower()
            if "thought" in normalized or "reasoning" in normalized:
                buffer["thought"] += text
            elif "message" in normalized or "text" in normalized or "chunk" in normalized:
                buffer["text"] += text

    def call(
        self,
        method: str,
        params: dict[str, Any] | None = None,
        req_id: int | str | None = None,
        timeout: float = 10.0,
    ) -> dict[str, Any] | None:
        if req_id is None:
            req_id = next(self.request_ids)
        request = {
            "jsonrpc": "2.0",
            "id": req_id,
            "method": method,
            "params": params or {},
        }
        assert self.proc.stdin is not None
        self.proc.stdin.write(json.dumps(request, ensure_ascii=False) + "\n")
        self.proc.stdin.flush()
        key = str(req_id)
        start = time.time()
        while time.time() - start < timeout:
            with self.lock:
                if key in self.response_map:
                    return self.response_map.pop(key)
            if self.proc.poll() is not None:
                break
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
            timeout=20,
        )
        if result is None:
            raise RuntimeError(f"Claude ACP initialize timed out: {self._stderr_tail()}")
        self._raise_on_error(result, "initialize")
        self.initialized = True
        return result

    def list_sessions(self) -> dict[str, Any] | None:
        return self.call("session/list", {}, req_id=2, timeout=10)

    def create_session(self, cwd: str | Path) -> AcpSession:
        workspace_dir = str(Path(cwd).expanduser().resolve())
        response = self.call("session/new", {"cwd": workspace_dir, "mcpServers": []}, req_id=3, timeout=30)
        self._raise_on_error(response, "session/new")
        session_id = self._extract_session_id(response)
        if not session_id:
            raise RuntimeError(f"Claude ACP session/new did not return sessionId: {self._stderr_tail()}")
        return AcpSession(session_id=session_id, cwd=workspace_dir, default=True)

    def prompt(self, session_id: str, text: str) -> AcpPromptResult:
        self._reset_buffer(session_id)
        response = self.call(
            "session/prompt",
            {
                "sessionId": session_id,
                "prompt": [{"type": "text", "text": text}],
            },
            req_id=4,
            timeout=60,
        )
        self._raise_on_error(response, "session/prompt")
        response_text, response_thought = self._extract_prompt_result(response)
        buffered = self._consume_buffer(session_id)
        return AcpPromptResult(
            text=response_text or buffered["text"],
            thought=response_thought or buffered["thought"],
            raw=response,
        )

    def _reset_buffer(self, session_id: str) -> None:
        with self.lock:
            self.stream_buffers[session_id] = {"thought": "", "text": ""}
            self.stream_buffers["_default"] = {"thought": "", "text": ""}

    def _consume_buffer(self, session_id: str) -> dict[str, str]:
        with self.lock:
            current = self.stream_buffers.pop(session_id, {"thought": "", "text": ""})
            fallback = self.stream_buffers.pop("_default", {"thought": "", "text": ""})
        return {
            "thought": current["thought"] or fallback["thought"],
            "text": current["text"] or fallback["text"],
        }

    def _extract_session_id(self, value: Any) -> str:
        if isinstance(value, dict):
            for key in ("sessionId", "session_id", "id"):
                current = value.get(key)
                if isinstance(current, str) and current.strip():
                    return current.strip()
            for key in ("session", "result", "data"):
                nested = self._extract_session_id(value.get(key))
                if nested:
                    return nested
            for nested_value in value.values():
                nested = self._extract_session_id(nested_value)
                if nested:
                    return nested
        if isinstance(value, list):
            for item in value:
                nested = self._extract_session_id(item)
                if nested:
                    return nested
        return ""

    def _extract_prompt_result(self, response: dict[str, Any] | None) -> tuple[str, str]:
        if not isinstance(response, dict):
            return "", ""
        result = response.get("result")
        if not isinstance(result, dict):
            return "", ""
        text = self._extract_text(result.get("output") or result.get("message") or result.get("content") or result.get("text"))
        thought = self._extract_text(result.get("thought") or result.get("reasoning") or result.get("thinking"))
        return text, thought

    def _extract_text(self, value: Any) -> str:
        if value is None:
            return ""
        if isinstance(value, str):
            return value
        if isinstance(value, dict):
            for key in ("text", "content", "message", "value", "delta"):
                text = self._extract_text(value.get(key))
                if text:
                    return text
            parts = [self._extract_text(item) for item in value.values()]
            return "".join(part for part in parts if part)
        if isinstance(value, list):
            parts = [self._extract_text(item) for item in value]
            return "".join(part for part in parts if part)
        return ""

    def _stderr_tail(self) -> str:
        with self.lock:
            return " | ".join(self.stderr_lines[-5:])

    def _raise_on_error(self, response: dict[str, Any] | None, method: str) -> None:
        if not isinstance(response, dict):
            return
        error = response.get("error")
        if isinstance(error, dict):
            raise RuntimeError(f"Claude ACP {method} failed: {error}")
