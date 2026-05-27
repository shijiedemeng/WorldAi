from __future__ import annotations

import argparse
import importlib
import json
import os
import queue
import sys
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, quote, urlparse

from pymilvus import DataType, MilvusClient


ROOT_DIR = Path(__file__).resolve().parents[1]
DEFAULT_DB_PATH = ROOT_DIR / "state" / "milvus-lite" / "ai_api.db"
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 8091
DEFAULT_COLLECTION_PREFIX = os.getenv("MILVUS_COLLECTION_PREFIX", "project_knowledge")
MAX_TEXT_LENGTH = 60000
SSE_KEEPALIVE_SECONDS = 15
SSE_SESSIONS: dict[str, "McpSseSession"] = {}
SSE_LOCK = threading.Lock()


class VectorStore:
    def __init__(self, uri: str) -> None:
        self.uri = uri
        if uri.endswith(".db"):
            Path(uri).parent.mkdir(parents=True, exist_ok=True)
            self._check_milvus_lite_dependency()
        try:
            self.client = MilvusClient(uri=uri)
        except Exception as exc:
            if uri.endswith(".db") and "pkg_resources" in repr(exc):
                raise RuntimeError(
                    "Milvus Lite 已安装，但当前 setuptools 缺少 pkg_resources。"
                    "请执行：pip install -r milvusClient/requirements.txt；"
                    "其中 setuptools 需要降到 81 以下。"
                ) from exc
            if uri.endswith(".db") and ("milvus-lite" in str(exc) or "milvus_lite" in repr(exc)):
                raise RuntimeError(
                    "Milvus Lite 本地文件模式缺少依赖，请先执行："
                    "pip install -r milvusClient/requirements.txt；"
                    "如果只想连接独立 Milvus 服务，请用 --uri 指向服务地址。"
                ) from exc
            raise

    @staticmethod
    def _check_milvus_lite_dependency() -> None:
        try:
            importlib.import_module("milvus_lite")
        except ModuleNotFoundError as exc:
            missing_name = exc.name or ""
            if missing_name == "pkg_resources":
                raise RuntimeError(
                    "Milvus Lite 已安装，但当前 setuptools 缺少 pkg_resources。"
                    "请执行：pip install -r milvusClient/requirements.txt；"
                    "其中 setuptools 需要降到 81 以下。"
                ) from exc
            raise RuntimeError(
                "Milvus Lite 本地文件模式缺少依赖，请先执行："
                "pip install -r milvusClient/requirements.txt；"
                "如果只想连接独立 Milvus 服务，请用 --uri 指向服务地址。"
            ) from exc

    def upsert(self, collection: str, vector_id: str, vector: list[float], metadata: dict[str, Any], content: str) -> dict[str, Any]:
        self._ensure_collection(collection, len(vector))
        self.client.upsert(
            collection_name=collection,
            data=[
                {
                    "id": vector_id,
                    "vector": vector,
                    "content": trim_text(content),
                    "metadataJson": json.dumps(metadata or {}, ensure_ascii=False),
                }
            ],
        )
        return {"collection": collection, "id": vector_id, "dbPath": self.uri}

    def search(self, collection: str, vector: list[float], limit: int) -> dict[str, Any]:
        if not self.client.has_collection(collection):
            return {"collection": collection, "items": []}
        rows = self.client.search(
            collection_name=collection,
            data=[vector],
            limit=max(1, min(limit, 100)),
            output_fields=["content", "metadataJson"],
        )
        items: list[dict[str, Any]] = []
        for row in rows[0] if rows else []:
            entity = row.get("entity") or {}
            metadata_raw = entity.get("metadataJson") or "{}"
            try:
                metadata = json.loads(metadata_raw)
            except json.JSONDecodeError:
                metadata = {}
            items.append(
                {
                    "id": row.get("id"),
                    "score": row.get("distance"),
                    "content": entity.get("content"),
                    "metadata": metadata,
                }
            )
        return {"collection": collection, "items": items}

    def delete(self, collection: str, vector_id: str) -> dict[str, Any]:
        if self.client.has_collection(collection):
            self.client.delete(collection_name=collection, ids=[vector_id])
        return {"collection": collection, "id": vector_id, "deleted": True}

    def _ensure_collection(self, collection: str, dimension: int) -> None:
        if self.client.has_collection(collection):
            return
        schema = MilvusClient.create_schema(auto_id=False, enable_dynamic_field=False)
        schema.add_field("id", DataType.VARCHAR, is_primary=True, max_length=128)
        schema.add_field("vector", DataType.FLOAT_VECTOR, dim=dimension)
        schema.add_field("content", DataType.VARCHAR, max_length=MAX_TEXT_LENGTH)
        schema.add_field("metadataJson", DataType.VARCHAR, max_length=MAX_TEXT_LENGTH)
        index_params = self.client.prepare_index_params()
        index_params.add_index(field_name="vector", index_type="AUTOINDEX", metric_type="COSINE")
        self.client.create_collection(collection_name=collection, schema=schema, index_params=index_params)


class McpSseSession:
    def __init__(self, session_id: str) -> None:
        self.session_id = session_id
        self.events: "queue.Queue[tuple[str, dict[str, Any]]]" = queue.Queue()
        self.created_at = time.time()


class Handler(BaseHTTPRequestHandler):
    store: VectorStore

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self.write_json({
                "status": "ok",
                "uri": self.store.uri,
                "mcp": {
                    "transport": "sse",
                    "sseUrl": "/mcp/sse",
                    "messagesUrl": "/mcp/messages",
                    "toolsUrl": "/mcp/tools",
                },
            })
            return
        if parsed.path == "/mcp/tools":
            self.write_json({"tools": mcp_tools()})
            return
        if parsed.path in {"/mcp/sse", "/sse"}:
            self.open_mcp_sse()
            return
        self.write_json({"error": "not found"}, status=404)

    def do_POST(self) -> None:
        try:
            parsed = urlparse(self.path)
            payload = self.read_json()
            if parsed.path == "/vectors/upsert":
                self.write_json(self.store.upsert(
                    collection_from_payload(payload),
                    required_text(payload, "id"),
                    required_vector(payload, "vector"),
                    payload.get("metadata") or {},
                    payload.get("content") or "",
                ))
                return
            if parsed.path == "/vectors/search":
                self.write_json(self.store.search(
                    collection_from_payload(payload),
                    required_vector(payload, "vector"),
                    int(payload.get("limit") or 5),
                ))
                return
            if parsed.path == "/vectors/delete":
                self.write_json(self.store.delete(
                    collection_from_payload(payload),
                    required_text(payload, "id"),
                ))
                return
            if parsed.path in {"/mcp/messages", "/messages"}:
                self.handle_mcp_message(parsed, payload)
                return
            if parsed.path == "/mcp":
                self.write_json(handle_mcp_request(self.store, payload))
                return
            self.write_json({"error": "not found"}, status=404)
        except Exception as exc:
            self.write_json({"error": exc.__class__.__name__, "message": str(exc)}, status=500)

    def open_mcp_sse(self) -> None:
        session_id = "MCP_SSE-" + uuid.uuid4().hex
        session = McpSseSession(session_id)
        with SSE_LOCK:
            SSE_SESSIONS[session_id] = session
        try:
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.end_headers()
            endpoint = f"/mcp/messages?sessionId={quote(session_id)}"
            self.write_sse("endpoint", endpoint)
            while True:
                try:
                    event, data = session.events.get(timeout=SSE_KEEPALIVE_SECONDS)
                    self.write_sse(event, json.dumps(data, ensure_ascii=False))
                except queue.Empty:
                    self.wfile.write(b": keepalive\n\n")
                    self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError, OSError):
            pass
        finally:
            with SSE_LOCK:
                SSE_SESSIONS.pop(session_id, None)

    def handle_mcp_message(self, parsed: Any, payload: dict[str, Any]) -> None:
        query = parse_qs(parsed.query)
        session_id = (query.get("sessionId") or [""])[0].strip()
        if not session_id:
            self.write_json({"error": "sessionId is required"}, status=400)
            return
        with SSE_LOCK:
            session = SSE_SESSIONS.get(session_id)
        if session is None:
            self.write_json({"error": "mcp sse session not found"}, status=404)
            return
        response = handle_mcp_request(self.store, payload)
        if response is not None:
            session.events.put(("message", response))
        self.write_json({"accepted": True, "sessionId": session_id}, status=202)

    def write_sse(self, event: str, data: str) -> None:
        raw = f"event: {event}\ndata: {data}\n\n".encode("utf-8")
        self.wfile.write(raw)
        self.wfile.flush()

    def read_json(self) -> dict[str, Any]:
        transfer_encoding = (self.headers.get("Transfer-Encoding") or "").lower()
        if "chunked" in transfer_encoding:
            raw = self.read_chunked_body().decode("utf-8")
        else:
            length = int(self.headers.get("Content-Length") or "0")
            raw = self.rfile.read(length).decode("utf-8")
        if not raw:
            return {}
        value = json.loads(raw)
        if not isinstance(value, dict):
            raise ValueError("request body must be a JSON object")
        return value

    def read_chunked_body(self) -> bytes:
        chunks: list[bytes] = []
        while True:
            line = self.rfile.readline()
            if not line:
                break
            size_text = line.strip().split(b";", 1)[0]
            if not size_text:
                continue
            size = int(size_text, 16)
            if size == 0:
                while True:
                    trailer = self.rfile.readline()
                    if trailer in {b"\r\n", b"\n", b""}:
                        break
                break
            chunks.append(self.rfile.read(size))
            self.rfile.read(2)
        return b"".join(chunks)

    def write_json(self, payload: dict[str, Any], status: int = 200) -> None:
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, fmt: str, *args: Any) -> None:
        print("%s - %s" % (self.address_string(), fmt % args))


def required_text(payload: dict[str, Any], key: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{key} is required")
    return value.strip()


def required_vector(payload: dict[str, Any], key: str) -> list[float]:
    value = payload.get(key)
    if not isinstance(value, list) or not value:
        raise ValueError(f"{key} must be a non-empty number array")
    return [float(item) for item in value]


def collection_from_payload(payload: dict[str, Any]) -> str:
    project_code = first_text(
        payload.get("projectCode"),
        payload.get("project_code"),
        payload.get("project"),
    )
    metadata = payload.get("metadata")
    if project_code is None and isinstance(metadata, dict):
        project_code = first_text(
            metadata.get("projectCode"),
            metadata.get("project_code"),
            metadata.get("project"),
        )
    if isinstance(project_code, str) and project_code.strip():
        return project_collection_name(project_code)
    raise ValueError(f"projectCode is required, received keys: {sorted(payload.keys())}")


def first_text(*values: Any) -> str | None:
    for value in values:
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None


def project_collection_name(project_code: str, prefix: str = DEFAULT_COLLECTION_PREFIX) -> str:
    safe_prefix = sanitize_collection_part(prefix) or "project_knowledge"
    safe_project = sanitize_collection_part(project_code)
    if not safe_project:
        raise ValueError("projectCode is invalid")
    return f"{safe_prefix}_{safe_project}"


def sanitize_collection_part(value: str) -> str:
    chars = [char.lower() if char.isascii() and char.isalnum() else "_" for char in value.strip()]
    normalized = "".join(chars)
    while "__" in normalized:
        normalized = normalized.replace("__", "_")
    return normalized.strip("_")


def optional_int(payload: dict[str, Any], key: str, default: int) -> int:
    value = payload.get(key)
    if value is None:
        return default
    return int(value)


def handle_mcp_request(store: VectorStore, payload: dict[str, Any]) -> dict[str, Any] | None:
    request_id = payload.get("id")
    if request_id is None:
        return None
    try:
        method = payload.get("method") or ""
        if method == "initialize":
            result = {
                "protocolVersion": ((payload.get("params") or {}).get("protocolVersion") or "2024-11-05"),
                "capabilities": {"tools": {}},
                "serverInfo": {"name": "ai-api-milvus-lite", "version": "1.0.0"},
            }
        elif method == "ping":
            result = {}
        elif method == "tools/list":
            result = {"tools": mcp_tools()}
        elif method == "tools/call":
            result = handle_mcp_tool_call(store, payload.get("params") or {})
        else:
            return mcp_error(request_id, -32601, f"method not found: {method}")
        return {"jsonrpc": "2.0", "id": request_id, "result": result}
    except Exception as exc:
        return mcp_error(request_id, -32603, f"{exc.__class__.__name__}: {exc}")


def handle_mcp_tool_call(store: VectorStore, params: dict[str, Any]) -> dict[str, Any]:
    name = (params.get("name") or "").strip()
    arguments = params.get("arguments") or {}
    if not isinstance(arguments, dict):
        raise ValueError("tools/call arguments must be an object")
    if name in {"milvus_vector_search", "project_knowledge_vector_search", "search"}:
        result = store.search(
            collection_from_payload(arguments),
            required_vector(arguments, "vector"),
            optional_int(arguments, "limit", 5),
        )
        return mcp_tool_result(result)
    if name in {"milvus_vector_upsert", "upsert"}:
        result = store.upsert(
            collection_from_payload(arguments),
            required_text(arguments, "id"),
            required_vector(arguments, "vector"),
            arguments.get("metadata") or {},
            arguments.get("content") or "",
        )
        return mcp_tool_result(result)
    if name in {"milvus_vector_delete", "delete"}:
        result = store.delete(collection_from_payload(arguments), required_text(arguments, "id"))
        return mcp_tool_result(result)
    raise ValueError(f"unknown tool: {name}")


def mcp_tool_result(payload: dict[str, Any]) -> dict[str, Any]:
    return {
        "isError": False,
        "content": [
            {
                "type": "text",
                "text": json.dumps(payload, ensure_ascii=False, indent=2),
            }
        ],
    }


def mcp_error(request_id: Any, code: int, message: str) -> dict[str, Any]:
    return {
        "jsonrpc": "2.0",
        "id": request_id,
        "error": {"code": code, "message": message},
    }


def mcp_tools() -> list[dict[str, Any]]:
    return [
        {
            "name": "milvus_vector_search",
            "description": "在 Milvus Lite 集合中按向量查询相似项目储备知识。",
            "inputSchema": {
                "type": "object",
                "required": ["projectCode", "vector"],
                "properties": {
                    "projectCode": {"type": "string", "description": "项目列表里的项目编码，用于隔离查询范围"},
                    "vector": {"type": "array", "items": {"type": "number"}, "description": "查询向量"},
                    "limit": {"type": "integer", "description": "返回数量，默认 5，最大 100"},
                },
            },
        },
        {
            "name": "milvus_vector_upsert",
            "description": "写入或更新一条 Milvus Lite 向量记录。",
            "inputSchema": {
                "type": "object",
                "required": ["projectCode", "id", "vector"],
                "properties": {
                    "projectCode": {"type": "string"},
                    "id": {"type": "string"},
                    "vector": {"type": "array", "items": {"type": "number"}},
                    "content": {"type": "string"},
                    "metadata": {"type": "object"},
                },
            },
        },
        {
            "name": "milvus_vector_delete",
            "description": "删除一条 Milvus Lite 向量记录。",
            "inputSchema": {
                "type": "object",
                "required": ["projectCode", "id"],
                "properties": {
                    "projectCode": {"type": "string"},
                    "id": {"type": "string"},
                },
            },
        },
    ]


def trim_text(value: str) -> str:
    return value[:MAX_TEXT_LENGTH] if len(value) > MAX_TEXT_LENGTH else value


def main() -> int:
    parser = argparse.ArgumentParser(description="AI API Milvus Lite vector service")
    parser.add_argument("--host", default=os.getenv("MILVUS_CLIENT_HOST", DEFAULT_HOST))
    parser.add_argument("--port", type=int, default=int(os.getenv("MILVUS_CLIENT_PORT", str(DEFAULT_PORT))))
    parser.add_argument("--uri", default=os.getenv("MILVUS_URI", str(DEFAULT_DB_PATH)))
    args = parser.parse_args()
    try:
        Handler.store = VectorStore(args.uri)
    except RuntimeError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Milvus Lite vector service listening on http://{args.host}:{args.port}, uri={args.uri}")
    server.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
