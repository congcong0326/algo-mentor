#!/usr/bin/env python3
"""校验本地题库抓取缓存或最终题库 seed。"""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    sys.path.append(str(Path(__file__).resolve().parents[2]))

from tools.problem_seed.leetcode_api import (
    CN_SITE,
    COM_SITE,
    SITE_BASE_URLS,
    cache_path,
    question_from_response,
    read_json,
    read_jsonl,
    utc_now_iso,
    write_json,
)
from tools.problem_seed.prepare_seed import first_non_empty, read_difficulty, read_tags
from tools.problem_seed.profile_problems import CATEGORY_COMPLETE, classify_problem


DEFAULT_INDEX_PATH = Path("data/index/problem_index.jsonl")
DEFAULT_CACHE_DIR = Path("data/sources/leetcode-api")
DEFAULT_REPORT_PATH = Path("data/reports/validation_report.json")
ALLOWED_DIFFICULTIES = {"EASY", "MEDIUM", "HARD"}
ALLOWED_CONTENT_STATUSES = {"BILINGUAL", "CN_ONLY"}
ALLOWED_SOURCE_SITES = {"LEETCODE_COM_CN", "LEETCODE_CN"}


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate fetched LeetCode problem cache or final problem seed.")
    parser.add_argument("--index", default=str(DEFAULT_INDEX_PATH))
    parser.add_argument("--cache-dir", default=str(DEFAULT_CACHE_DIR))
    parser.add_argument("--seed", default="")
    parser.add_argument("--output", default=str(DEFAULT_REPORT_PATH))
    args = parser.parse_args()

    if args.seed:
        report = build_seed_validation_report(read_jsonl(Path(args.seed)), Path(args.seed))
    else:
        report = build_validation_report(read_jsonl(Path(args.index)), Path(args.cache_dir))
    write_json(Path(args.output), report)
    print({"checked": report["checkedCount"], "issueCount": report["issueCount"], "issuesByRule": report["issuesByRule"]})


def build_seed_validation_report(rows: list[dict[str, Any]], seed_path: Path) -> dict[str, Any]:
    issues: list[dict[str, Any]] = []
    seen_slugs: dict[str, str | int] = {}
    seen_frontend_ids: dict[str, str | int] = {}
    seen_frontend_display_ids: dict[str, str | int] = {}

    for row_index, row in enumerate(rows, start=1):
        slug = str(row.get("slug") or "")
        frontend_id = row.get("frontendId")
        frontend_display_id = row.get("frontendDisplayId")
        content_status = row.get("contentStatus")
        source_site = row.get("sourceSite")

        if not slug:
            issues.append({"rule": "missing_slug", "row": row_index})
        else:
            add_unique_issue(issues, "duplicate_slug", slug, seen_slugs, slug)
        if frontend_id is not None:
            if not isinstance(frontend_id, int):
                issues.append({"rule": "invalid_frontend_id", "slug": slug, "value": frontend_id})
            else:
                add_unique_issue(issues, "duplicate_frontend_id", slug, seen_frontend_ids, str(frontend_id))
        if frontend_display_id is None or str(frontend_display_id).strip() == "":
            issues.append({"rule": "missing_frontend_display_id", "slug": slug})
        else:
            add_unique_issue(
                issues,
                "duplicate_frontend_display_id",
                slug,
                seen_frontend_display_ids,
                str(frontend_display_id).strip(),
            )

        difficulty = read_difficulty({"difficulty": row.get("difficulty")})
        if difficulty not in ALLOWED_DIFFICULTIES:
            issues.append({"rule": "invalid_difficulty", "slug": slug, "value": row.get("difficulty")})
        if content_status not in ALLOWED_CONTENT_STATUSES:
            issues.append({"rule": "invalid_content_status", "slug": slug, "value": content_status})
        if source_site not in ALLOWED_SOURCE_SITES:
            issues.append({"rule": "invalid_source_site", "slug": slug, "value": source_site})

        issues.extend(validate_seed_required_content(slug, row))
        issues.extend(validate_seed_tags(slug, row))
        issues.extend(validate_seed_url(slug, row.get("leetcodeUrl")))

    issues_by_rule = Counter(str(issue["rule"]) for issue in issues)
    return {
        "generatedAt": utc_now_iso(),
        "seedPath": str(seed_path),
        "checkedCategory": "final_seed",
        "checkedCount": len(rows),
        "issueCount": len(issues),
        "issuesByRule": dict(issues_by_rule),
        "issues": issues[:500],
    }


def validate_seed_required_content(slug: str, row: dict[str, Any]) -> list[dict[str, Any]]:
    issues: list[dict[str, Any]] = []
    content_status = row.get("contentStatus")
    title_en = optional_text(row.get("titleEn"))
    title_zh = optional_text(row.get("titleZh"))
    content_en = optional_text(row.get("contentMarkdownEn"))
    content_zh = optional_text(row.get("contentMarkdownZh"))
    if content_status == "BILINGUAL":
        for field, value in (
            ("titleEn", title_en),
            ("titleZh", title_zh),
            ("contentMarkdownEn", content_en),
            ("contentMarkdownZh", content_zh),
        ):
            if value is None:
                issues.append({"rule": "missing_bilingual_content", "slug": slug, "field": field})
    elif content_status == "CN_ONLY":
        if title_en is not None:
            issues.append({"rule": "cn_only_english_title_not_null", "slug": slug})
        if content_en is not None:
            issues.append({"rule": "cn_only_english_content_not_null", "slug": slug})
        if title_zh is None:
            issues.append({"rule": "missing_cn_only_content", "slug": slug, "field": "titleZh"})
        if content_zh is None:
            issues.append({"rule": "missing_cn_only_content", "slug": slug, "field": "contentMarkdownZh"})
    return issues


def validate_seed_tags(slug: str, row: dict[str, Any]) -> list[dict[str, Any]]:
    issues: list[dict[str, Any]] = []
    tag_values = row.get("tagValues")
    tag_labels_en = row.get("tagLabelsEn")
    tag_labels_zh = row.get("tagLabelsZh")
    if not all(isinstance(value, list) for value in (tag_values, tag_labels_en, tag_labels_zh)):
        return [{"rule": "tag_arrays_not_lists", "slug": slug}]
    if not (len(tag_values) == len(tag_labels_en) == len(tag_labels_zh)):
        issues.append({
            "rule": "tag_array_length_mismatch",
            "slug": slug,
            "tagValues": len(tag_values),
            "tagLabelsEn": len(tag_labels_en),
            "tagLabelsZh": len(tag_labels_zh),
        })
    for field, values in (
        ("tagValues", tag_values),
        ("tagLabelsEn", tag_labels_en),
        ("tagLabelsZh", tag_labels_zh),
    ):
        if any(not isinstance(value, str) or not value.strip() for value in values):
            issues.append({"rule": "blank_tag_value", "slug": slug, "field": field})
    return issues


def validate_seed_url(slug: str, url: Any) -> list[dict[str, Any]]:
    text = optional_text(url)
    if text is None:
        return []
    extracted = extract_slug_from_leetcode_url(text)
    if extracted != slug:
        return [{"rule": "leetcode_url_slug_mismatch", "slug": slug, "url": text, "extractedSlug": extracted}]
    return []


def build_validation_report(rows: list[dict[str, Any]], cache_dir: Path) -> dict[str, Any]:
    issues: list[dict[str, Any]] = []
    seen_slugs: dict[str, int] = {}
    seen_frontend_ids: dict[str, str] = {}
    checked_count = 0

    for row in rows:
        slug = str(row.get("slug") or "")
        difficulty = read_difficulty({"difficulty": row.get("difficulty")})
        add_unique_issue(issues, "duplicate_slug", slug, seen_slugs, slug)
        frontend_id = row.get("frontendId")
        if frontend_id is not None:
            add_unique_issue(issues, "duplicate_frontend_id", slug, seen_frontend_ids, str(frontend_id))
        if difficulty not in ALLOWED_DIFFICULTIES:
            issues.append({"rule": "invalid_difficulty", "slug": slug, "value": row.get("difficulty")})

        com_raw = read_optional(cache_path(cache_dir, slug, COM_SITE))
        cn_raw = read_optional(cache_path(cache_dir, slug, CN_SITE))
        category = classify_problem(row, com_raw, cn_raw)
        if category != CATEGORY_COMPLETE:
            continue

        checked_count += 1
        com_question = question_from_response(com_raw)
        cn_question = question_from_response(cn_raw)
        issues.extend(validate_normalizable_problem(slug, com_question, cn_question))

    issues_by_rule = Counter(str(issue["rule"]) for issue in issues)
    return {
        "generatedAt": utc_now_iso(),
        "totalIndexCount": len(rows),
        "checkedCategory": CATEGORY_COMPLETE,
        "checkedCount": checked_count,
        "issueCount": len(issues),
        "issuesByRule": dict(issues_by_rule),
        "issues": issues[:500],
    }


def validate_normalizable_problem(
    slug: str,
    com_question: dict[str, Any] | None,
    cn_question: dict[str, Any] | None,
) -> list[dict[str, Any]]:
    issues: list[dict[str, Any]] = []
    if not com_question or not cn_question:
        issues.append({"rule": "missing_question_payload", "slug": slug})
        return issues

    com_slug = as_slug(first_non_empty(com_question.get("titleSlug"), slug))
    cn_slug = as_slug(first_non_empty(cn_question.get("titleSlug"), slug))
    if com_slug != slug:
        issues.append({"rule": "com_title_slug_mismatch", "slug": slug, "value": com_question.get("titleSlug")})
    if cn_slug != slug:
        issues.append({"rule": "cn_title_slug_mismatch", "slug": slug, "value": cn_question.get("titleSlug")})

    for site, url in ((COM_SITE, leetcode_url(COM_SITE, slug)), (CN_SITE, leetcode_url(CN_SITE, slug))):
        extracted = extract_slug_from_leetcode_url(url)
        if extracted != slug:
            issues.append({"rule": "leetcode_url_slug_mismatch", "slug": slug, "site": site, "url": url})

    tag_values, tag_labels_en, tag_labels_zh = merged_tag_arrays(com_question, cn_question)
    if not (len(tag_values) == len(tag_labels_en) == len(tag_labels_zh)):
        issues.append({
            "rule": "tag_array_length_mismatch",
            "slug": slug,
            "tagValues": len(tag_values),
            "tagLabelsEn": len(tag_labels_en),
            "tagLabelsZh": len(tag_labels_zh),
        })
    return issues


def merged_tag_arrays(com_question: dict[str, Any], cn_question: dict[str, Any]) -> tuple[list[str], list[str], list[str]]:
    tag_values, tag_labels_en, fallback_zh = read_tags(com_question)
    cn_tags = cn_question.get("topicTags")
    zh_by_slug: dict[str, str] = {}
    if isinstance(cn_tags, list):
        for tag in cn_tags:
            if isinstance(tag, dict) and tag.get("slug"):
                zh_by_slug[str(tag["slug"])] = str(first_non_empty(
                    tag.get("translatedName"),
                    tag.get("name"),
                    tag.get("slug"),
                ))
    tag_labels_zh = [zh_by_slug.get(value, fallback_zh[index]) for index, value in enumerate(tag_values)]
    return tag_values, tag_labels_en, tag_labels_zh


def add_unique_issue(
    issues: list[dict[str, Any]],
    rule: str,
    slug: str,
    seen: dict[str, str | int],
    value: str,
) -> None:
    if value in seen:
        issues.append({"rule": rule, "slug": slug, "value": value, "firstSeen": seen[value]})
    else:
        seen[value] = slug


def read_optional(path: Path) -> Any | None:
    return read_json(path) if path.exists() else None


def leetcode_url(site: str, slug: str) -> str:
    return f"{SITE_BASE_URLS[site]}/problems/{slug}/"


def extract_slug_from_leetcode_url(url: str) -> str | None:
    match = re.search(r"/problems/([^/?#]+)/?", url)
    return as_slug(match.group(1)) if match else None


def as_slug(value: Any) -> str | None:
    if value is None:
        return None
    slug = str(value).strip()
    return slug or None


def optional_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


if __name__ == "__main__":
    main()
