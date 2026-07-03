#!/usr/bin/env python3
"""读取本地 LeetCode API 缓存并生成题库数据画像。"""

from __future__ import annotations

import argparse
import sys
from collections import Counter
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    sys.path.append(str(Path(__file__).resolve().parents[2]))

from tools.problem_seed.leetcode_api import (
    CN_SITE,
    COM_SITE,
    cache_path,
    non_empty_text,
    question_from_response,
    read_json,
    read_jsonl,
    utc_now_iso,
    write_json,
)
from tools.problem_seed.prepare_seed import first_non_empty


DEFAULT_INDEX_PATH = Path("data/index/problem_index.jsonl")
DEFAULT_CACHE_DIR = Path("data/sources/leetcode-api")
DEFAULT_REPORT_PATH = Path("data/reports/profile_report.json")

CATEGORY_COMPLETE = "complete"
CATEGORY_ZH_MISSING = "zh_missing"
CATEGORY_CN_ONLY = "cn_only"
CATEGORY_PREMIUM_LOCKED = "premium_locked"
CATEGORY_DEPRECATED = "deprecated"
CATEGORIES = [
    CATEGORY_COMPLETE,
    CATEGORY_ZH_MISSING,
    CATEGORY_CN_ONLY,
    CATEGORY_PREMIUM_LOCKED,
    CATEGORY_DEPRECATED,
]


def main() -> None:
    parser = argparse.ArgumentParser(description="Profile fetched LeetCode problem cache.")
    parser.add_argument("--index", default=str(DEFAULT_INDEX_PATH))
    parser.add_argument("--cache-dir", default=str(DEFAULT_CACHE_DIR))
    parser.add_argument("--output", default=str(DEFAULT_REPORT_PATH))
    args = parser.parse_args()

    rows = read_jsonl(Path(args.index))
    report = build_profile_report(rows, Path(args.cache_dir))
    write_json(Path(args.output), report)
    print_summary(report)


def build_profile_report(rows: list[dict[str, Any]], cache_dir: Path) -> dict[str, Any]:
    counts = Counter({category: 0 for category in CATEGORIES})
    free_counts = Counter({category: 0 for category in CATEGORIES})
    difficulty_distribution: dict[str, Counter[str]] = {category: Counter() for category in CATEGORIES}
    examples: dict[str, list[str]] = {category: [] for category in CATEGORIES}
    tag_distribution: Counter[str] = Counter()
    frontend_id_mismatches: list[dict[str, Any]] = []

    for row in rows:
        slug = str(row.get("slug") or "")
        cached = read_cached_pair(cache_dir, slug)
        category = classify_problem(row, cached.get(COM_SITE), cached.get(CN_SITE))
        counts[category] += 1
        if not row.get("paidOnly"):
            free_counts[category] += 1
        difficulty = str(row.get("difficulty") or "UNKNOWN")
        difficulty_distribution[category][difficulty] += 1
        if len(examples[category]) < 20:
            examples[category].append(slug)

        com_question = question_from_response(cached.get(COM_SITE))
        cn_question = question_from_response(cached.get(CN_SITE))
        for tag_slug in read_tag_slugs(com_question or cn_question):
            tag_distribution[tag_slug] += 1
        mismatch = frontend_id_mismatch(slug, com_question, cn_question)
        if mismatch is not None:
            frontend_id_mismatches.append(mismatch)

    return {
        "generatedAt": utc_now_iso(),
        "total": len(rows),
        "counts": dict(counts),
        "freeCounts": dict(free_counts),
        "difficultyDistribution": {
            category: dict(difficulty_distribution[category])
            for category in CATEGORIES
        },
        "examples": examples,
        "tagDistributionTop50": dict(tag_distribution.most_common(50)),
        "frontendIdMismatches": frontend_id_mismatches,
        "frontendIdMismatchCount": len(frontend_id_mismatches),
    }


def read_cached_pair(cache_dir: Path, slug: str) -> dict[str, Any | None]:
    result: dict[str, Any | None] = {}
    for site in (COM_SITE, CN_SITE):
        path = cache_path(cache_dir, slug, site)
        result[site] = read_json(path) if path.exists() else None
    return result


def classify_problem(row: dict[str, Any], com_raw: Any | None, cn_raw: Any | None) -> str:
    com_question = question_from_response(com_raw)
    cn_question = question_from_response(cn_raw)
    has_com_content = non_empty_text(com_question.get("content") if com_question else None)
    has_cn_content = non_empty_text(cn_question.get("translatedContent") if cn_question else None)

    if has_com_content and has_cn_content:
        return CATEGORY_COMPLETE
    if has_com_content and not has_cn_content:
        return CATEGORY_ZH_MISSING
    if not has_com_content and has_cn_content:
        return CATEGORY_CN_ONLY
    if bool(row.get("paidOnly")):
        return CATEGORY_PREMIUM_LOCKED
    return CATEGORY_DEPRECATED


def frontend_id_mismatch(
    slug: str,
    com_question: dict[str, Any] | None,
    cn_question: dict[str, Any] | None,
) -> dict[str, Any] | None:
    if not com_question or not cn_question:
        return None
    com_id = first_non_empty(com_question.get("questionFrontendId"), com_question.get("questionId"))
    cn_id = first_non_empty(cn_question.get("questionFrontendId"), cn_question.get("questionId"))
    if com_id is None or cn_id is None or str(com_id) == str(cn_id):
        return None
    return {"slug": slug, "comFrontendId": str(com_id), "cnFrontendId": str(cn_id)}


def read_tag_slugs(question: dict[str, Any] | None) -> list[str]:
    tags = question.get("topicTags") if isinstance(question, dict) else None
    if not isinstance(tags, list):
        return []
    return [
        str(tag.get("slug"))
        for tag in tags
        if isinstance(tag, dict) and tag.get("slug")
    ]


def print_summary(report: dict[str, Any]) -> None:
    print({
        "total": report["total"],
        "counts": report["counts"],
        "freeCounts": report["freeCounts"],
        "frontendIdMismatchCount": report["frontendIdMismatchCount"],
    })


if __name__ == "__main__":
    main()
