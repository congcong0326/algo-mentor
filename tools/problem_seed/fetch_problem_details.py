#!/usr/bin/env python3
"""按 union 索引抓取 LeetCode GraphQL 单题原始 JSON。"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path
from typing import Any, Callable

if __package__ in (None, ""):
    sys.path.append(str(Path(__file__).resolve().parents[2]))

from tools.problem_seed.leetcode_api import (
    CN_SITE,
    COM_SITE,
    DEFAULT_REQUEST_INTERVAL_SECONDS,
    DEFAULT_RETRY_COUNT,
    GRAPHQL_ENDPOINTS,
    cache_path,
    graphql_headers,
    graphql_payload,
    question_from_response,
    read_jsonl,
    request_json,
    write_json,
)


DEFAULT_INDEX_PATH = Path("data/index/problem_index.jsonl")
DEFAULT_CACHE_DIR = Path("data/sources/leetcode-api")


def main() -> None:
    parser = argparse.ArgumentParser(description="Fetch LeetCode GraphQL problem details.")
    parser.add_argument("--index", default=str(DEFAULT_INDEX_PATH))
    parser.add_argument("--cache-dir", default=str(DEFAULT_CACHE_DIR))
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--slug", action="append", default=[], help="只抓指定 slug，可重复传入。")
    parser.add_argument("--force", action="store_true", help="覆盖已存在缓存。")
    parser.add_argument("--interval", type=float, default=DEFAULT_REQUEST_INTERVAL_SECONDS)
    parser.add_argument("--retries", type=int, default=DEFAULT_RETRY_COUNT)
    args = parser.parse_args()

    rows = select_rows(read_jsonl(Path(args.index)), args.slug, args.limit)
    stats = fetch_details(
        rows,
        Path(args.cache_dir),
        force=args.force,
        interval_seconds=args.interval,
        retries=args.retries,
    )
    print(stats)


def select_rows(rows: list[dict[str, Any]], slugs: list[str], limit: int | None) -> list[dict[str, Any]]:
    if slugs:
        wanted = set(slugs)
        rows = [row for row in rows if row.get("slug") in wanted]
    if limit is not None:
        rows = rows[:limit]
    return rows


def fetch_details(
    rows: list[dict[str, Any]],
    cache_dir: Path,
    *,
    force: bool = False,
    interval_seconds: float = DEFAULT_REQUEST_INTERVAL_SECONDS,
    retries: int = DEFAULT_RETRY_COUNT,
    sleeper: Callable[[float], None] = time.sleep,
) -> dict[str, int]:
    stats = {"fetched": 0, "skipped": 0, "failed": 0, "questionNull": 0}
    for row in rows:
        slug = str(row.get("slug") or "")
        if not slug:
            continue
        for site, enabled_key in ((COM_SITE, "onCom"), (CN_SITE, "onCn")):
            if not row.get(enabled_key):
                continue
            result = fetch_detail(
                slug,
                site,
                cache_dir,
                force=force,
                retries=retries,
                sleeper=sleeper,
            )
            stats[result] += 1
            if result == "fetched":
                raw = read_cached(cache_dir, slug, site)
                if question_from_response(raw) is None:
                    stats["questionNull"] += 1
                sleeper(interval_seconds)
    return stats


def fetch_detail(
    slug: str,
    site: str,
    cache_dir: Path,
    *,
    force: bool = False,
    retries: int = DEFAULT_RETRY_COUNT,
    sleeper: Callable[[float], None] = time.sleep,
) -> str:
    path = cache_path(cache_dir, slug, site)
    if path.exists() and not force:
        return "skipped"

    result = request_json(
        GRAPHQL_ENDPOINTS[site],
        method="POST",
        payload=graphql_payload(slug),
        headers=graphql_headers(site, slug),
        retries=retries,
        retry_sleep=sleeper,
    )
    if result.status != 200:
        print(f"Fetch detail failed: slug={slug} site={site} error={result.error or result.status}")
        return "failed"
    write_json(path, result.body)
    return "fetched"


def read_cached(cache_dir: Path, slug: str, site: str) -> Any:
    from tools.problem_seed.leetcode_api import read_json

    return read_json(cache_path(cache_dir, slug, site))


if __name__ == "__main__":
    main()
