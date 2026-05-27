#!/usr/bin/env python3
from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any, Mapping
from urllib import error, parse, request

SKILL_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = SKILL_ROOT / "skill-config.json"


@dataclass
class ApiError(Exception):
    message: str
    http_status: int | None = None
    body: str = ""

    def __str__(self) -> str:
        if self.http_status is None:
            return self.message
        return f"{self.message} http={self.http_status}"


class AiApiClient:
    def __init__(self, base_url: str, timeout: float = 30.0) -> None:
        normalized = base_url.rstrip("/")
        self.api_base = normalized if normalized.endswith("/api") else f"{normalized}/api"
        self.timeout = timeout

    def get(self, path: str, query: Mapping[str, Any] | None = None) -> Any:
        return self._request("GET", path, query=query)

    def post(self, path: str, payload: Mapping[str, Any] | None = None) -> Any:
        return self._request("POST", path, payload=payload)

    def _request(
        self,
        method: str,
        path: str,
        payload: Mapping[str, Any] | None = None,
        query: Mapping[str, Any] | None = None,
    ) -> Any:
        url = f"{self.api_base}{path if path.startswith('/') else '/' + path}"
        if query:
            query_items = [(key, str(value)) for key, value in query.items() if value not in {None, ""}]
            if query_items:
                url = f"{url}?{parse.urlencode(query_items)}"
        body = None
        headers = {"Accept": "application/json"}
        if payload is not None:
            headers["Content-Type"] = "application/json"
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        req = request.Request(url, data=body, headers=headers, method=method)
        try:
            with request.urlopen(req, timeout=self.timeout) as response:
                raw = response.read().decode("utf-8")
        except error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            raise ApiError(raw or str(exc), exc.code, raw) from exc
        except error.URLError as exc:
            raise ApiError(f"network error: {exc.reason}") from exc
        parsed = json.loads(raw) if raw.strip() else {}
        if not isinstance(parsed, dict):
            raise ApiError("invalid JSON response", body=raw)
        if int(parsed.get("code") or 0) != 0:
            raise ApiError(str(parsed.get("message") or "application error"), body=raw)
        return parsed.get("data")


def print_json(value: Any) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2))


def default_base_url() -> str:
    if not CONFIG_PATH.exists():
        return "http://127.0.0.1:8080"
    try:
        parsed = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return "http://127.0.0.1:8080"
    if not isinstance(parsed, dict):
        return "http://127.0.0.1:8080"
    return str(parsed.get("url") or parsed.get("baseUrl") or "http://127.0.0.1:8080")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="ai-api project knowledge language search helper")
    parser.add_argument("--base-url", default=default_base_url())
    parser.add_argument("--timeout", type=float, default=60.0)
    parser.add_argument(
        "--transport",
        choices=["http", "rest"],
        default="http",
        help="普通 HTTP/REST 调用模式。SSE MCP 请由支持 MCP 的会话直接配置，不通过本脚本转发。",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    projects = subparsers.add_parser("projects", help="list project code/name")
    projects.add_argument("--keyword")
    projects.add_argument("--limit", type=int, default=50)

    search = subparsers.add_parser("search", help="language search project knowledge")
    search.add_argument("--project-code", required=True)
    search.add_argument("--query", required=True)
    search.add_argument("--knowledge-type", choices=["COMMON_ISSUE", "PROCESS_GUIDE"])
    search.add_argument("--embedding-setting-key")
    search.add_argument("--limit", type=int, default=5)
    search.add_argument("--min-match-score", type=float)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    client = AiApiClient(args.base_url, args.timeout)
    if args.command == "projects":
        print_json(client.get("/mcp/project-knowledge/projects", {
            "keyword": args.keyword,
            "limit": args.limit,
        }))
        return 0
    if args.command == "search":
        print_json(client.post("/mcp/project-knowledge/search", {
            "projectCode": args.project_code,
            "query": args.query,
            "knowledgeType": args.knowledge_type,
            "embeddingSettingKey": args.embedding_setting_key,
            "limit": args.limit,
            "minMatchScore": args.min_match_score,
        }))
        return 0
    raise AssertionError(args.command)


if __name__ == "__main__":
    raise SystemExit(main())
