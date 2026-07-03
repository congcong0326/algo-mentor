#!/usr/bin/env python3
"""LeetCode 官方 API 抓取与本地缓存的共享工具。"""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Iterable


COM_SITE = "com"
CN_SITE = "cn"

INDEX_URLS = {
    COM_SITE: "https://leetcode.com/api/problems/all/",
    CN_SITE: "https://leetcode.cn/api/problems/all/",
}

GRAPHQL_ENDPOINTS = {
    COM_SITE: "https://leetcode.com/graphql",
    CN_SITE: "https://leetcode.cn/graphql",
}

SITE_BASE_URLS = {
    COM_SITE: "https://leetcode.com",
    CN_SITE: "https://leetcode.cn",
}

DEFAULT_USER_AGENT = "algo-mentor-problem-seed/1.0 (+https://leetcode.com)"
DEFAULT_RETRY_COUNT = 3
DEFAULT_REQUEST_INTERVAL_SECONDS = 0.75
DEFAULT_TIMEOUT_SECONDS = 30

QUESTION_DATA_QUERY = (
    "query questionData($titleSlug: String!) { "
    "question(titleSlug: $titleSlug) { "
    "questionId questionFrontendId title titleSlug content translatedTitle translatedContent "
    "difficulty isPaidOnly topicTags { name slug translatedName } "
    "codeSnippets { lang langSlug code } sampleTestCase exampleTestcases "
    "} "
    "}"
)


@dataclass(frozen=True)
class HttpResult:
    status: int
    body: Any
    error: str | None = None


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open(encoding="utf-8") as file:
        for line in file:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as file:
        for row in rows:
            file.write(json.dumps(row, ensure_ascii=False, separators=(",", ":"), sort_keys=True) + "\n")


def request_json(
    url: str,
    *,
    method: str = "GET",
    payload: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: int = DEFAULT_TIMEOUT_SECONDS,
    retries: int = DEFAULT_RETRY_COUNT,
    retry_sleep: Callable[[float], None] = time.sleep,
) -> HttpResult:
    request_headers = {"User-Agent": DEFAULT_USER_AGENT, **(headers or {})}
    data = None
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request_headers.setdefault("Content-Type", "application/json")

    last_error: str | None = None
    attempts = max(1, retries)
    for attempt in range(attempts):
        request = urllib.request.Request(url, data=data, headers=request_headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                raw = response.read().decode("utf-8")
                return HttpResult(response.status, json.loads(raw) if raw else None)
        except urllib.error.HTTPError as error:
            raw = error.read().decode("utf-8", errors="replace")
            try:
                body: Any = json.loads(raw) if raw else None
            except json.JSONDecodeError:
                body = raw
            if 400 <= error.code < 500 and error.code != 429:
                return HttpResult(error.code, body, f"HTTP {error.code}")
            last_error = f"HTTP {error.code}"
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
            last_error = str(error)

        if attempt < attempts - 1:
            retry_sleep(2 ** attempt)

    return HttpResult(0, None, last_error)


def graphql_payload(slug: str) -> dict[str, Any]:
    return {
        "operationName": "questionData",
        "variables": {"titleSlug": slug},
        "query": QUESTION_DATA_QUERY,
    }


def graphql_headers(site: str, slug: str) -> dict[str, str]:
    base_url = SITE_BASE_URLS[site]
    return {
        "Content-Type": "application/json",
        "User-Agent": DEFAULT_USER_AGENT,
        "Referer": f"{base_url}/problems/{slug}/",
    }


def cache_path(cache_dir: Path, slug: str, site: str) -> Path:
    return cache_dir / f"{slug}.{site}.json"


def question_from_response(raw: Any) -> dict[str, Any] | None:
    if not isinstance(raw, dict):
        return None
    data = raw.get("data")
    if not isinstance(data, dict):
        return None
    question = data.get("question")
    return question if isinstance(question, dict) else None


def non_empty_text(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())

