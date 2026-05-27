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


class CodexAcpClient:
    def __init__(self, command: str = "codex app-server --listen stdio://") -> None:
        self.proc = subprocess.Popen(
            shlex.split(command),
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
        self.response_map: dict[str, dict[str, Any]] = {}
        self.completed_turns: dict[str, dict[str, Any]] = {}
        self.turn_buffers: dict[str, dict[str, str]] = {}
        self.thread_latest_turns: dict[str, str] = {}
        self.thread_cwds: dict[str, str] = {}
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
            if "method" in msg:
                self._handle_notification(msg)

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

    def _handle_notification(self, msg: dict[str, Any]) -> None:
        method = str(msg.get("method") or "")
        params = msg.get("params")
        if not isinstance(params, dict):
            return
        thread_id = str(params.get("threadId") or "")
        turn = params.get("turn") if isinstance(params.get("turn"), dict) else {}
        turn_id = str((turn or {}).get("id") or params.get("turnId") or "")

        if method in {"turn/started", "turn/completed"}:
            if thread_id and turn_id:
                with self.lock:
                    self.thread_latest_turns[thread_id] = turn_id

        if method == "turn/completed" and turn_id:
            with self.lock:
                self.completed_turns[turn_id] = msg
            return

        if not turn_id:
            return

        delta = str(params.get("delta") or "")
        with self.lock:
            buffer = self.turn_buffers.setdefault(turn_id, {"text": "", "thought": ""})
            if method == "item/agentMessage/delta":
                buffer["text"] += delta
            elif method in {"item/reasoning/textDelta", "item/reasoning/summaryTextDelta"}:
                buffer["thought"] += delta

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
                "clientInfo": {
                    "name": "ai-api-python-client",
                    "title": "ai-api-python-client",
                    "version": "1.0",
                },
                "capabilities": {
                    "experimentalApi": True,
                    "optOutNotificationMethods": [],
                },
            },
            req_id=1,
            timeout=20,
        )
        if result is None:
            raise RuntimeError(f"Codex ACP initialize timed out: {self._stderr_tail()}")
        self._raise_on_error(result, "initialize")
        self.initialized = True
        return result

    def list_sessions(self) -> dict[str, Any] | None:
        return self.call("thread/list", {}, req_id=2, timeout=10)

    def create_session(self, cwd: str | Path) -> AcpSession:
        workspace_dir = str(Path(cwd).expanduser().resolve())
        response = self.call(
            "thread/start",
            {
                "cwd": workspace_dir,
                "approvalPolicy": "never",
                "sandbox": "workspace-write",
                "ephemeral": True,
                "sessionStartSource": "startup",
                "threadSource": "user",
            },
            req_id=3,
            timeout=30,
        )
        self._raise_on_error(response, "thread/start")
        thread = (response or {}).get("result", {}).get("thread", {}) if isinstance(response, dict) else {}
        session_id = str(thread.get("id") or "")
        if not session_id:
            raise RuntimeError(f"Codex thread/start did not return thread id: {self._stderr_tail()}")
        with self.lock:
            self.thread_cwds[session_id] = workspace_dir
        return AcpSession(session_id=session_id, cwd=workspace_dir, default=True)

    def prompt(self, session_id: str, text: str) -> AcpPromptResult:
        workspace_dir = self._thread_cwd(session_id)
        if not workspace_dir:
            raise RuntimeError(f"Codex ACP thread not initialized for session {session_id}")
        response = self.call(
            "turn/start",
            {
                "threadId": session_id,
                "input": [
                    {
                        "type": "text",
                        "text": text,
                        "text_elements": [],
                    }
                ],
                "cwd": workspace_dir,
                "approvalPolicy": "never",
                "sandboxPolicy": {
                    "type": "workspaceWrite",
                    "writableRoots": [workspace_dir],
                    "networkAccess": False,
                    "excludeTmpdirEnvVar": False,
                    "excludeSlashTmp": False,
                },
            },
            req_id=4,
            timeout=20,
        )
        self._raise_on_error(response, "turn/start")
        turn_id = self._extract_turn_id(response, session_id)
        if not turn_id:
            raise RuntimeError(f"Codex turn/start did not return turn id: {self._stderr_tail()}")

        response_turn = self._extract_response_turn(response)
        completed = None
        if not response_turn or str(response_turn.get("status") or "") not in {"completed", "failed", "interrupted"}:
            completed = self._wait_for_turn_completed(turn_id, timeout=120)
        turn_payload = self._extract_turn_payload(completed, response, turn_id)
        text_output, thought_output = self._extract_turn_output(turn_payload)
        buffered = self._consume_turn_buffer(turn_id)
        return AcpPromptResult(
            text=text_output or buffered["text"],
            thought=thought_output or buffered["thought"],
            raw=completed or response,
        )

    def _wait_for_turn_completed(self, turn_id: str, timeout: float) -> dict[str, Any] | None:
        start = time.time()
        while time.time() - start < timeout:
            with self.lock:
                if turn_id in self.completed_turns:
                    return self.completed_turns.pop(turn_id)
            if self.proc.poll() is not None:
                break
            time.sleep(0.05)
        return None

    def _extract_turn_id(self, response: dict[str, Any] | None, session_id: str) -> str:
        result = (response or {}).get("result", {}) if isinstance(response, dict) else {}
        turn = result.get("turn") if isinstance(result.get("turn"), dict) else {}
        turn_id = str(turn.get("id") or "")
        if turn_id:
            return turn_id
        with self.lock:
            return self.thread_latest_turns.get(session_id, "")

    def _extract_turn_payload(
        self,
        completed: dict[str, Any] | None,
        response: dict[str, Any] | None,
        turn_id: str,
    ) -> dict[str, Any]:
        for payload in (
            (completed or {}).get("params", {}).get("turn") if isinstance(completed, dict) else None,
            (response or {}).get("result", {}).get("turn") if isinstance(response, dict) else None,
        ):
            if isinstance(payload, dict):
                return payload
        return {"id": turn_id, "items": []}

    def _extract_response_turn(self, response: dict[str, Any] | None) -> dict[str, Any]:
        if not isinstance(response, dict):
            return {}
        result = response.get("result")
        if not isinstance(result, dict):
            return {}
        turn = result.get("turn")
        return turn if isinstance(turn, dict) else {}

    def _extract_turn_output(self, turn: dict[str, Any]) -> tuple[str, str]:
        text_parts: list[str] = []
        thought_parts: list[str] = []
        for item in turn.get("items", []):
            if not isinstance(item, dict):
                continue
            item_type = str(item.get("type") or "")
            if item_type == "agentMessage":
                text = str(item.get("text") or "").strip()
                if not text:
                    continue
                if item.get("phase") == "commentary":
                    thought_parts.append(text)
                else:
                    text_parts.append(text)
                continue
            if item_type == "reasoning":
                summaries = [str(summary).strip() for summary in item.get("summary", []) if str(summary).strip()]
                content = [str(content).strip() for content in item.get("content", []) if str(content).strip()]
                if summaries:
                    thought_parts.extend(summaries)
                elif content:
                    thought_parts.extend(content)
        return "\n".join(text_parts).strip(), "\n".join(thought_parts).strip()

    def _consume_turn_buffer(self, turn_id: str) -> dict[str, str]:
        with self.lock:
            return self.turn_buffers.pop(turn_id, {"text": "", "thought": ""})

    def _thread_cwd(self, session_id: str) -> str:
        with self.lock:
            return self.thread_cwds.get(session_id, "")

    def _stderr_tail(self) -> str:
        with self.lock:
            return " | ".join(self.stderr_lines[-5:])

    def _raise_on_error(self, response: dict[str, Any] | None, method: str) -> None:
        if not isinstance(response, dict):
            return
        error = response.get("error")
        if isinstance(error, dict):
            raise RuntimeError(f"Codex ACP {method} failed: {error}")
