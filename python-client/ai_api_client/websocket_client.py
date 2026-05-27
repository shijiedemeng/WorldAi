from __future__ import annotations

import base64
import hashlib
import json
import os
import socket
import ssl
import struct
import threading
import time
from typing import Callable
from urllib.parse import urlparse


class ClientDispatchWebSocket:
    def __init__(
        self,
        base_url: str,
        client_code: str,
        on_command: Callable[[dict], None],
        on_session_create: Callable[[dict], None] | None = None,
        on_mcp_file_request: Callable[[dict], None] | None = None,
    ) -> None:
        self.base_url = base_url
        self.client_code = client_code
        self.on_command = on_command
        self.on_session_create = on_session_create
        self.on_mcp_file_request = on_mcp_file_request
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._sock: socket.socket | None = None
        self._send_lock = threading.Lock()

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        try:
            if self._sock:
                self._sock.close()
        except OSError:
            pass

    def _run(self) -> None:
        while not self._stop.is_set():
            try:
                self._connect_and_read()
            except OSError:
                pass
            except Exception:
                pass
            if not self._stop.is_set():
                time.sleep(5)

    def _connect_and_read(self) -> None:
        parsed = urlparse(self.base_url)
        secure = parsed.scheme == "https"
        host = parsed.hostname or "127.0.0.1"
        port = parsed.port or (443 if secure else 80)
        path = "/ws/clients"
        raw_sock = socket.create_connection((host, port), timeout=5)
        self._sock = ssl.create_default_context().wrap_socket(raw_sock, server_hostname=host) if secure else raw_sock
        self._handshake(host, port, path)
        self._sock.settimeout(None)
        self._send_json({"type": "REGISTER", "clientCode": self.client_code})
        self._start_heartbeat(self._sock)
        while not self._stop.is_set():
            message = self._recv_text()
            if message is None:
                return
            if not message:
                continue
            payload = json.loads(message)
            if payload.get("type") == "EXECUTE_COMMAND" and isinstance(payload.get("data"), dict):
                self.on_command(payload["data"])
            elif payload.get("type") == "CREATE_SESSION" and isinstance(payload.get("data"), dict) and self.on_session_create:
                self.on_session_create(payload["data"])
            elif payload.get("type") == "MCP_FILE_REQUEST" and isinstance(payload.get("data"), dict) and self.on_mcp_file_request:
                threading.Thread(target=self.on_mcp_file_request, args=(payload["data"],), daemon=True).start()

    def _handshake(self, host: str, port: int, path: str) -> None:
        key = base64.b64encode(os.urandom(16)).decode("ascii")
        request = (
            f"GET {path} HTTP/1.1\r\n"
            f"Host: {host}:{port}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n"
            "\r\n"
        )
        assert self._sock is not None
        self._sock.sendall(request.encode("ascii"))
        response = self._recv_until(b"\r\n\r\n")
        if b" 101 " not in response.split(b"\r\n", 1)[0]:
            raise OSError("websocket handshake failed")
        expected = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode("ascii")).digest())
        if expected not in response:
            raise OSError("websocket accept key mismatch")

    def _recv_until(self, marker: bytes) -> bytes:
        assert self._sock is not None
        data = b""
        while marker not in data:
            chunk = self._sock.recv(1024)
            if not chunk:
                raise OSError("connection closed")
            data += chunk
        return data

    def _send_json(self, payload: dict) -> None:
        self._send_text(json.dumps(payload, ensure_ascii=False))

    def _send_text(self, text: str) -> None:
        assert self._sock is not None
        payload = text.encode("utf-8")
        header = bytearray([0x81])
        length = len(payload)
        if length < 126:
            header.append(0x80 | length)
        elif length <= 0xFFFF:
            header.append(0x80 | 126)
            header.extend(struct.pack("!H", length))
        else:
            header.append(0x80 | 127)
            header.extend(struct.pack("!Q", length))
        mask = os.urandom(4)
        masked = bytes(item ^ mask[index % 4] for index, item in enumerate(payload))
        with self._send_lock:
            self._sock.sendall(bytes(header) + mask + masked)

    def _send_pong(self, payload: bytes) -> None:
        assert self._sock is not None
        header = bytearray([0x8A])
        length = len(payload)
        header.append(0x80 | length)
        mask = os.urandom(4)
        masked = bytes(item ^ mask[index % 4] for index, item in enumerate(payload))
        with self._send_lock:
            self._sock.sendall(bytes(header) + mask + masked)

    def _start_heartbeat(self, sock: socket.socket) -> None:
        def loop() -> None:
            while not self._stop.is_set() and self._sock is sock:
                time.sleep(15)
                if self._stop.is_set() or self._sock is not sock:
                    return
                try:
                    self._send_json({"type": "HEARTBEAT", "clientCode": self.client_code})
                except OSError:
                    return

        threading.Thread(target=loop, daemon=True).start()

    def _recv_text(self) -> str | None:
        assert self._sock is not None
        first = self._read_exact(2)
        if not first:
            return None
        opcode = first[0] & 0x0F
        masked = bool(first[1] & 0x80)
        length = first[1] & 0x7F
        if length == 126:
            length = struct.unpack("!H", self._read_exact(2))[0]
        elif length == 127:
            length = struct.unpack("!Q", self._read_exact(8))[0]
        mask = self._read_exact(4) if masked else b""
        payload = self._read_exact(length) if length else b""
        if masked:
            payload = bytes(item ^ mask[index % 4] for index, item in enumerate(payload))
        if opcode == 0x8:
            return None
        if opcode == 0x9:
            self._send_pong(payload)
            return ""
        if opcode != 0x1:
            return ""
        return payload.decode("utf-8")

    def _read_exact(self, size: int) -> bytes:
        assert self._sock is not None
        data = b""
        while len(data) < size:
            chunk = self._sock.recv(size - len(data))
            if not chunk:
                raise OSError("connection closed")
            data += chunk
        return data
