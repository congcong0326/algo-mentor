#!/usr/bin/env python3
"""抓取 LeetCode 两站题目清单并生成 union 索引。"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    sys.path.append(str(Path(__file__).resolve().parents[2]))

from tools.problem_seed.leetcode_api import (
    CN_SITE,
    COM_SITE,
    INDEX_URLS,
    request_json,
    utc_now_iso,
    write_json,
    write_jsonl,
)
from tools.problem_seed.prepare_seed import first_non_empty, nested, read_difficulty


DEFAULT_OUTPUT_DIR = Path("data/index")


def main() -> None:
    parser = argparse.ArgumentParser(description="Fetch LeetCode problem index from .com and .cn.")
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    parser.add_argument("--verify-only", action="store_true", help="只打印接口结构预览，不写入文件。")
    args = parser.parse_args()

    raw_by_site = fetch_indexes()
    for site, raw in raw_by_site.items():
        print_index_preview(site, raw)

    if args.verify_only:
        return

    com_records = parse_index(raw_by_site[COM_SITE], COM_SITE)
    cn_records = parse_index(raw_by_site[CN_SITE], CN_SITE)
    rows = merge_indexes(com_records, cn_records)
    manifest = build_manifest(raw_by_site, rows)
    write_outputs(Path(args.output_dir), rows, manifest)
    print(f"Wrote {len(rows)} union problems to {Path(args.output_dir) / 'problem_index.jsonl'}")


def fetch_indexes() -> dict[str, Any]:
    raw_by_site: dict[str, Any] = {}
    for site, url in INDEX_URLS.items():
        result = request_json(url, headers={"Referer": f"https://leetcode.{site if site == CN_SITE else 'com'}/"})
        if result.status != 200:
            raise RuntimeError(f"Fetch index failed for {site}: {result.error or result.status}")
        raw_by_site[site] = result.body
    return raw_by_site


def print_index_preview(site: str, raw: Any, limit: int = 3) -> None:
    pairs = raw.get("stat_status_pairs") if isinstance(raw, dict) else []
    print(f"[{site}] stat_status_pairs={len(pairs) if isinstance(pairs, list) else 'invalid'}")
    if not isinstance(pairs, list):
        return
    for item in pairs[:limit]:
        stat = item.get("stat") if isinstance(item, dict) else {}
        difficulty = item.get("difficulty") if isinstance(item, dict) else {}
        print({
            "slug": nested({"stat": stat}, "stat", "question__title_slug"),
            "frontendId": nested({"stat": stat}, "stat", "frontend_question_id"),
            "title": nested({"stat": stat}, "stat", "question__title"),
            "paidOnly": item.get("paid_only") if isinstance(item, dict) else None,
            "difficultyLevel": difficulty.get("level") if isinstance(difficulty, dict) else None,
        })


def parse_index(raw: Any, site: str) -> list[dict[str, Any]]:
    pairs = raw.get("stat_status_pairs") if isinstance(raw, dict) else None
    if not isinstance(pairs, list):
        raise ValueError(f"Index response for {site} missing stat_status_pairs")

    records: list[dict[str, Any]] = []
    for item in pairs:
        if not isinstance(item, dict):
            continue
        stat = item.get("stat") if isinstance(item.get("stat"), dict) else {}
        difficulty = item.get("difficulty") if isinstance(item.get("difficulty"), dict) else {}
        slug = as_slug(first_non_empty(stat.get("question__title_slug"), stat.get("title_slug")))
        if not slug:
            continue
        frontend_id = as_frontend_id(first_non_empty(stat.get("frontend_question_id"), stat.get("question_id")))
        records.append({
            "slug": slug,
            "frontendId": frontend_id,
            "difficulty": read_difficulty({"level": difficulty.get("level")}),
            "paidOnly": bool(item.get("paid_only")),
            "onCom": site == COM_SITE,
            "onCn": site == CN_SITE,
        })
    return records


def merge_indexes(com_records: list[dict[str, Any]], cn_records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    merged: dict[str, dict[str, Any]] = {}
    for site, records in ((COM_SITE, com_records), (CN_SITE, cn_records)):
        for record in records:
            slug = record["slug"]
            existing = merged.setdefault(slug, {
                "slug": slug,
                "frontendId": None,
                "difficulty": None,
                "paidOnly": False,
                "onCom": False,
                "onCn": False,
            })
            if site == COM_SITE:
                existing["onCom"] = True
                existing["frontendId"] = first_non_empty(record.get("frontendId"), existing.get("frontendId"))
                existing["difficulty"] = first_non_empty(record.get("difficulty"), existing.get("difficulty"))
            else:
                existing["onCn"] = True
                existing["frontendId"] = first_non_empty(existing.get("frontendId"), record.get("frontendId"))
                existing["difficulty"] = first_non_empty(existing.get("difficulty"), record.get("difficulty"))
            existing["paidOnly"] = bool(existing["paidOnly"] or record.get("paidOnly"))
    return [merged[slug] for slug in sorted(merged)]


def build_manifest(raw_by_site: dict[str, Any], rows: list[dict[str, Any]]) -> dict[str, Any]:
    com_count = len(parse_index(raw_by_site[COM_SITE], COM_SITE))
    cn_count = len(parse_index(raw_by_site[CN_SITE], CN_SITE))
    both_count = sum(1 for row in rows if row.get("onCom") and row.get("onCn"))
    com_only_count = sum(1 for row in rows if row.get("onCom") and not row.get("onCn"))
    cn_only_count = sum(1 for row in rows if row.get("onCn") and not row.get("onCom"))
    return {
        "source": "leetcode-api",
        "fetchedAt": utc_now_iso(),
        "comCount": com_count,
        "cnCount": cn_count,
        "unionCount": len(rows),
        "intersectionCount": both_count,
        "comOnlyCount": com_only_count,
        "cnOnlyCount": cn_only_count,
        "indexUrls": INDEX_URLS,
    }


def write_outputs(output_dir: Path, rows: list[dict[str, Any]], manifest: dict[str, Any]) -> None:
    write_jsonl(output_dir / "problem_index.jsonl", rows)
    write_json(output_dir / "manifest.json", manifest)


def as_slug(value: Any) -> str | None:
    if value is None:
        return None
    slug = str(value).strip()
    return slug or None


def as_frontend_id(value: Any) -> int | str | None:
    try:
        return int(str(value))
    except (TypeError, ValueError):
        text = str(value).strip() if value is not None else ""
        return text or None


if __name__ == "__main__":
    main()
