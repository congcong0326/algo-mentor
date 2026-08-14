#!/usr/bin/env python3
"""抓取 LeetCode 学习元数据到独立、可恢复的原始缓存。"""

from __future__ import annotations

import argparse
import hashlib
import sys
import time
from collections import Counter
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
    question_from_response,
    read_json,
    read_jsonl,
    request_json,
    utc_now_iso,
    write_json,
)
from tools.problem_seed.problem_metadata_contract import (
    FETCH_REPORT_FILE,
    METADATA_GRAPHQL_OPERATION,
    METADATA_GRAPHQL_QUERY,
    METADATA_QUERY_VERSION,
)


DEFAULT_SEED_PATH = Path("data/seed/problems.jsonl")
DEFAULT_INDEX_PATH = Path("data/index/problem_index.jsonl")
DEFAULT_CACHE_DIR = Path("data/sources/leetcode-api-metadata")


def main() -> None:
    parser = argparse.ArgumentParser(description="Fetch LeetCode learning metadata into a separate raw cache.")
    parser.add_argument("--seed", default=str(DEFAULT_SEED_PATH))
    parser.add_argument("--index", default=str(DEFAULT_INDEX_PATH))
    parser.add_argument("--cache-dir", default=str(DEFAULT_CACHE_DIR))
    parser.add_argument("--slug", action="append", default=[], help="仅抓指定 slug，可重复传入。")
    parser.add_argument("--site", action="append", choices=(COM_SITE, CN_SITE), default=[],
                        help="仅抓指定站点；可重复传入，用于并行但独立限速的缓存回填。")
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--force", action="store_true", help="覆盖已存在的成功缓存。")
    parser.add_argument("--interval", type=float, default=DEFAULT_REQUEST_INTERVAL_SECONDS)
    parser.add_argument("--retries", type=int, default=DEFAULT_RETRY_COUNT)
    parser.add_argument("--no-report", action="store_true",
                        help="只回填缓存，不覆盖完整终态报告；并行站点抓取结束后应再运行一次普通命令。")
    args = parser.parse_args()

    report = fetch_metadata(
        Path(args.seed), Path(args.index), Path(args.cache_dir),
        slugs=args.slug,
        limit=args.limit,
        force=args.force,
        interval_seconds=args.interval,
        retries=args.retries,
        selected_sites=set(args.site) or None,
        write_report=not args.no_report,
    )
    print(report["summary"])
    if report["summary"]["failedFetchCount"]:
        raise SystemExit(1)


def metadata_graphql_payload(slug: str) -> dict[str, Any]:
    return {
        "operationName": METADATA_GRAPHQL_OPERATION,
        "variables": {"titleSlug": slug},
        "query": METADATA_GRAPHQL_QUERY,
    }


def select_source_rows(
    seed_rows: list[dict[str, Any]],
    index_rows: list[dict[str, Any]],
    slugs: list[str],
    limit: int | None,
) -> list[dict[str, Any]]:
    index_by_slug = {str(row.get("slug")): row for row in index_rows if row.get("slug")}
    wanted = set(slugs)
    selected: list[dict[str, Any]] = []
    for seed in sorted(seed_rows, key=lambda row: str(row.get("slug") or "")):
        slug = str(seed.get("slug") or "").strip()
        if not slug or (wanted and slug not in wanted):
            continue
        index = index_by_slug.get(slug)
        if index is not None:
            sites = [site for site, key in ((COM_SITE, "onCom"), (CN_SITE, "onCn")) if index.get(key)]
        else:
            source_site = str(seed.get("sourceSite") or "")
            sites = [site for site, marker in ((COM_SITE, "COM"), (CN_SITE, "CN")) if marker in source_site]
        selected.append({"slug": slug, "sites": sites})
    return selected[:limit] if limit is not None else selected


def fetch_metadata(
    seed_path: Path,
    index_path: Path,
    cache_dir: Path,
    *,
    slugs: list[str] | None = None,
    limit: int | None = None,
    force: bool = False,
    interval_seconds: float = DEFAULT_REQUEST_INTERVAL_SECONDS,
    retries: int = DEFAULT_RETRY_COUNT,
    sleeper: Callable[[float], None] = time.sleep,
    selected_sites: set[str] | None = None,
    write_report: bool = True,
) -> dict[str, Any]:
    seed_rows = read_jsonl(seed_path)
    index_rows = read_jsonl(index_path) if index_path.exists() else []
    rows = select_source_rows(seed_rows, index_rows, slugs or [], limit)
    if selected_sites is not None:
        rows = [{**row, "sites": [site for site in row["sites"] if site in selected_sites]} for row in rows]
    site_states: list[dict[str, Any]] = []
    source_states: list[dict[str, Any]] = []

    for row in rows:
        slug = row["slug"]
        sites = row["sites"]
        per_source: list[str] = []
        if not sites:
            source_states.append({"slug": slug, "status": "notAvailable", "sites": []})
            continue
        for site in sites:
            state = fetch_one(
                slug, site, cache_dir, force=force, retries=retries, sleeper=sleeper)
            site_states.append({"slug": slug, "site": site, **state})
            per_source.append(state["status"])
            if state["status"] == "fetched" and state.get("cacheState") == "written":
                sleeper(interval_seconds)
        source_status = "failed" if "failed" in per_source else (
            "fetched" if "fetched" in per_source else "notAvailable")
        source_states.append({"slug": slug, "status": source_status, "sites": sites})

    input_sha = sha256_file(seed_path)
    cached_files = sorted(
        (path for path in cache_dir.glob("*.json") if path.name != FETCH_REPORT_FILE),
        key=lambda path: path.name)
    cache_checksums = {path.name: sha256_file(path) for path in cached_files}
    source_snapshot = f"{METADATA_QUERY_VERSION}@{sha256_text(canonical_json(cache_checksums))[:16]}"
    site_counts = Counter(state["status"] for state in site_states)
    source_counts = Counter(state["status"] for state in source_states)
    report = {
        "queryVersion": METADATA_QUERY_VERSION,
        "querySha256": sha256_text(METADATA_GRAPHQL_QUERY),
        "inputProblemsPath": str(seed_path),
        "inputProblemsSha256": input_sha,
        "sourceSnapshot": source_snapshot,
        "completedAt": utc_now_iso(),
        "sourceStates": source_states,
        "siteStates": site_states,
        "cacheChecksums": cache_checksums,
        "summary": {
            "sourceProblemCount": len(rows),
            "sourceStatusCounts": dict(sorted(source_counts.items())),
            "siteStatusCounts": dict(sorted(site_counts.items())),
            "failedFetchCount": source_counts["failed"],
            "notAvailableCount": source_counts["notAvailable"],
            "fetchedCount": source_counts["fetched"],
        },
    }
    if write_report:
        write_json(cache_dir / FETCH_REPORT_FILE, report)
    return report


def fetch_one(
    slug: str,
    site: str,
    cache_dir: Path,
    *,
    force: bool,
    retries: int,
    sleeper: Callable[[float], None],
) -> dict[str, Any]:
    path = cache_path(cache_dir, slug, site)
    if path.exists() and not force:
        try:
            if question_from_response(read_json(path)) is not None:
                return {"status": "fetched", "cacheState": "reused"}
        except (OSError, ValueError):
            pass

    result = request_json(
        GRAPHQL_ENDPOINTS[site],
        method="POST",
        payload=metadata_graphql_payload(slug),
        headers=graphql_headers(site, slug),
        retries=retries,
        retry_sleep=sleeper,
    )
    if result.status == 200 and question_from_response(result.body) is not None:
        write_json(path, result.body)
        return {"status": "fetched", "cacheState": "written"}
    if result.status in {400, 404} or (result.status == 200 and question_from_response(result.body) is None):
        return {"status": "notAvailable", "error": result.error or "question payload is unavailable"}
    return {"status": "failed", "error": result.error or f"HTTP {result.status}"}


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def canonical_json(value: Any) -> str:
    import json

    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)


if __name__ == "__main__":
    main()
