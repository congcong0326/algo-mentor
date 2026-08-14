#!/usr/bin/env python3
"""由学习元数据原始缓存确定性构建可审核的 JSONL seed。"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    sys.path.append(str(Path(__file__).resolve().parents[2]))

from tools.problem_seed.leetcode_api import CN_SITE, COM_SITE, cache_path, question_from_response, read_json, read_jsonl, write_json, write_jsonl
from tools.problem_seed.prepare_seed import html_to_markdown
from tools.problem_seed.problem_metadata_contract import (
    AUDIT_REPORT_FILE,
    CATEGORY_ITEMS_FILE,
    CATEGORY_TITLE_MAP,
    CATEGORIES_FILE,
    CODE_TEMPLATES_FILE,
    FETCH_REPORT_FILE,
    HINTS_FILE,
    MANIFEST_FILE,
    METADATA_QUERY_VERSION,
    METADATA_SOURCE,
    OUTPUT_FILES,
    RELATION_TYPE_LEETCODE_SIMILAR,
    RELATIONS_FILE,
    SITE_TO_SOURCE_SITE,
)


DEFAULT_PROBLEM_SEED = Path("data/seed/problems.jsonl")
DEFAULT_CACHE_DIR = Path("data/sources/leetcode-api-metadata")
DEFAULT_OUTPUT_DIR = Path("data/problem-metadata-seed")


def main() -> None:
    parser = argparse.ArgumentParser(description="Build deterministic LeetCode learning metadata seed files.")
    parser.add_argument("--problem-seed", default=str(DEFAULT_PROBLEM_SEED))
    parser.add_argument("--cache-dir", default=str(DEFAULT_CACHE_DIR))
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    args = parser.parse_args()
    result = build_seed(Path(args.problem_seed), Path(args.cache_dir))
    write_seed(Path(args.output_dir), result)
    print(result["stats"])
    if result["errors"]:
        raise SystemExit("Seed build errors: " + json.dumps(result["errors"], ensure_ascii=False))


def build_seed(problem_seed_path: Path, cache_dir: Path) -> dict[str, Any]:
    problems = read_jsonl(problem_seed_path)
    problem_by_slug = {normal_slug(row.get("slug")): row for row in problems if normal_slug(row.get("slug"))}
    fetch_report_path = cache_dir / FETCH_REPORT_FILE
    if not fetch_report_path.exists():
        raise ValueError(f"Missing metadata fetch report: {fetch_report_path}")
    fetch_report = read_json(fetch_report_path)
    if fetch_report.get("queryVersion") != METADATA_QUERY_VERSION:
        raise ValueError("Metadata fetch report query version does not match the fixed contract.")
    if fetch_report.get("inputProblemsSha256") != sha256_file(problem_seed_path):
        raise ValueError("Metadata fetch report was not produced from the current problem seed.")

    rows_by_slug_site: dict[tuple[str, str], dict[str, Any]] = {}
    source_sites: dict[str, list[str]] = {}
    for source_state in fetch_report.get("sourceStates", []):
        slug = normal_slug(source_state.get("slug"))
        if slug in problem_by_slug:
            source_sites[slug] = sorted({SITE_TO_SOURCE_SITE[site] for site in source_state.get("sites", []) if site in SITE_TO_SOURCE_SITE})
    for slug in sorted(problem_by_slug):
        for site in (COM_SITE, CN_SITE):
            raw = read_optional(cache_path(cache_dir, slug, site))
            question = question_from_response(raw) if raw is not None else None
            if question is not None:
                rows_by_slug_site[(slug, site)] = question

    errors: list[dict[str, Any]] = []
    warnings: list[dict[str, Any]] = []
    relations = build_relations(
        problem_by_slug, rows_by_slug_site, fetch_report.get("sourceSnapshot"), errors, warnings)
    hints = build_hints(rows_by_slug_site, fetch_report.get("sourceSnapshot"), errors)
    templates = build_templates(problem_by_slug, rows_by_slug_site, fetch_report.get("sourceSnapshot"), errors)
    categories, category_items = build_categories(rows_by_slug_site, fetch_report.get("sourceSnapshot"), errors)
    verify_python3_templates(problem_by_slug, templates, errors)
    output_rows = {
        RELATIONS_FILE: relations,
        HINTS_FILE: hints,
        CODE_TEMPLATES_FILE: templates,
        CATEGORIES_FILE: categories,
        CATEGORY_ITEMS_FILE: category_items,
    }
    stats = {
        "sourceProblemCount": len(problem_by_slug),
        "sourceProblemsWithCache": len({slug for slug, _ in rows_by_slug_site}),
        "relationCount": len(relations),
        "hintCount": len(hints),
        "codeTemplateCount": len(templates),
        "categoryCount": len(categories),
        "categoryItemCount": len(category_items),
        "errorCount": len(errors),
        "excludedSelfReferenceCount": len(warnings),
        "sourceStatusCounts": fetch_report.get("summary", {}).get("sourceStatusCounts", {}),
        "siteStatusCounts": fetch_report.get("summary", {}).get("siteStatusCounts", {}),
        "failedFetchCount": fetch_report.get("summary", {}).get("failedFetchCount", 0),
        "notAvailableCount": fetch_report.get("summary", {}).get("notAvailableCount", 0),
    }
    return {
        "outputRows": output_rows,
        "errors": sorted(errors, key=canonical_json),
        "warnings": sorted(warnings, key=canonical_json),
        "stats": stats,
        "problemSeedPath": str(problem_seed_path),
        "problemSeedSha256": sha256_file(problem_seed_path),
        "fetchReport": fetch_report,
        "fetchReportPath": str(fetch_report_path),
        "sourceProblemSites": [
            {"problemSlug": slug, "sourceSites": source_sites.get(slug, [])}
            for slug in sorted(problem_by_slug)
        ],
    }


def build_relations(
    problem_by_slug: dict[str, dict[str, Any]],
    questions: dict[tuple[str, str], dict[str, Any]],
    source_snapshot: str | None,
    errors: list[dict[str, Any]],
    warnings: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    merged: dict[tuple[str, str], dict[str, Any]] = {}
    for (source_slug, site), question in sorted(questions.items()):
        raw = question.get("similarQuestions")
        if raw in (None, ""):
            continue
        try:
            values = json.loads(raw) if isinstance(raw, str) else raw
        except json.JSONDecodeError as error:
            errors.append({"rule": "similar_questions_parse_error", "problemSlug": source_slug, "site": site, "error": str(error)})
            continue
        if not isinstance(values, list):
            errors.append({"rule": "similar_questions_not_array", "problemSlug": source_slug, "site": site})
            continue
        for value in values:
            if not isinstance(value, dict):
                errors.append({"rule": "similar_question_not_object", "problemSlug": source_slug, "site": site})
                continue
            target_slug = normal_slug(value.get("titleSlug"))
            if not target_slug:
                errors.append({"rule": "similar_question_empty_slug", "problemSlug": source_slug, "site": site})
                continue
            if target_slug == source_slug:
                warnings.append({
                    "rule": "similar_question_self_reference",
                    "problemSlug": source_slug,
                    "site": site,
                })
                continue
            key = (source_slug, target_slug)
            record = merged.setdefault(key, {
                "sourceSlug": source_slug,
                "targetSlug": target_slug,
                "relationType": RELATION_TYPE_LEETCODE_SIMILAR,
                "source": METADATA_SOURCE,
                "sourceSnapshot": source_snapshot,
                "metadata": {"sourceSites": []},
            })
            metadata = record["metadata"]
            metadata["sourceSites"] = sorted(set(metadata["sourceSites"]) | {SITE_TO_SOURCE_SITE[site]})
            merge_relation_metadata(metadata, value, site)
    return [merged[key] for key in sorted(merged)]


def merge_relation_metadata(metadata: dict[str, Any], value: dict[str, Any], site: str) -> None:
    # 译名以中文站为优先；其余辅助字段仅用于审计，不覆盖本地题库事实。
    if value.get("title") and not metadata.get("title"):
        metadata["title"] = str(value["title"]).strip()
    translated = non_empty(value.get("translatedTitle"))
    if translated and (site == CN_SITE or not metadata.get("translatedTitle")):
        metadata["translatedTitle"] = translated
    difficulty = non_empty(value.get("difficulty"))
    if difficulty and not metadata.get("difficulty"):
        metadata["difficulty"] = difficulty.upper()
    if isinstance(value.get("paidOnly"), bool) and "paidOnly" not in metadata:
        metadata["paidOnly"] = value["paidOnly"]


def build_hints(
    questions: dict[tuple[str, str], dict[str, Any]],
    source_snapshot: str | None,
    errors: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for (slug, site), question in sorted(questions.items()):
        raw_hints = question.get("hints") or []
        if not isinstance(raw_hints, list):
            errors.append({"rule": "hints_not_array", "problemSlug": slug, "site": site})
            continue
        for ordinal, hint in enumerate(raw_hints, start=1):
            content = clean_hint_markdown(hint)
            if not content:
                errors.append({"rule": "empty_hint", "problemSlug": slug, "site": site, "ordinal": ordinal})
                continue
            rows.append({
                "problemSlug": slug,
                "sourceSite": SITE_TO_SOURCE_SITE[site],
                "ordinal": ordinal,
                "contentMarkdown": content,
                "sourceSnapshot": source_snapshot,
            })
    return sorted(rows, key=lambda row: (row["problemSlug"], row["sourceSite"], row["ordinal"]))


def clean_hint_markdown(value: Any) -> str | None:
    if not isinstance(value, str) or not value.strip():
        return None
    markdown = html_to_markdown(value).strip()
    # 题面转换器会保留 sup；hint 的独立渲染契约不允许来源 HTML 残留。
    markdown = re.sub(r"</?[^>]+>", "", markdown).strip()
    return markdown or None


def build_templates(
    problem_by_slug: dict[str, dict[str, Any]],
    questions: dict[tuple[str, str], dict[str, Any]],
    source_snapshot: str | None,
    errors: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for slug in sorted(problem_by_slug):
        selected: dict[str, dict[str, Any]] = {}
        for site in (COM_SITE, CN_SITE):
            question = questions.get((slug, site))
            if question is None:
                continue
            seen_on_site: set[str] = set()
            snippets = question.get("codeSnippets") or []
            if not isinstance(snippets, list):
                errors.append({"rule": "code_snippets_not_array", "problemSlug": slug, "site": site})
                continue
            for snippet in snippets:
                if not isinstance(snippet, dict):
                    errors.append({"rule": "code_snippet_not_object", "problemSlug": slug, "site": site})
                    continue
                language_slug = normal_slug(snippet.get("langSlug"))
                label = non_empty(snippet.get("lang"))
                code = snippet.get("code").strip() if isinstance(snippet.get("code"), str) else None
                if not language_slug or not label or not code or not code.strip():
                    errors.append({"rule": "invalid_code_template", "problemSlug": slug, "site": site, "languageSlug": language_slug})
                    continue
                if language_slug in seen_on_site:
                    errors.append({"rule": "duplicate_code_language_on_site", "problemSlug": slug, "site": site, "languageSlug": language_slug})
                    continue
                seen_on_site.add(language_slug)
                selected.setdefault(language_slug, {
                    "problemSlug": slug,
                    "languageSlug": language_slug,
                    "languageLabel": label,
                    "code": code,
                    "sourceSite": SITE_TO_SOURCE_SITE[site],
                    "sourceSnapshot": source_snapshot,
                })
        rows.extend(selected.values())
    return sorted(rows, key=lambda row: (row["problemSlug"], row["languageSlug"]))


def build_categories(
    questions: dict[tuple[str, str], dict[str, Any]],
    source_snapshot: str | None,
    errors: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    category_rows: dict[str, dict[str, Any]] = {}
    item_rows: dict[tuple[str, str], dict[str, Any]] = {}
    for (slug, _site), question in sorted(questions.items()):
        title = non_empty(question.get("categoryTitle"))
        if not title:
            continue
        category = CATEGORY_TITLE_MAP.get(title)
        if category is None:
            errors.append({"rule": "unknown_category_title", "problemSlug": slug, "categoryTitle": title})
            continue
        category_slug = category["slug"]
        category_rows[category_slug] = {
            **category,
            "source": METADATA_SOURCE,
            "sourceSnapshot": source_snapshot,
        }
        item_rows[(slug, category_slug)] = {
            "problemSlug": slug,
            "categorySlug": category_slug,
            "source": METADATA_SOURCE,
            "sourceSnapshot": source_snapshot,
        }
    return ([category_rows[key] for key in sorted(category_rows)], [item_rows[key] for key in sorted(item_rows)])


def verify_python3_templates(
    problem_by_slug: dict[str, dict[str, Any]],
    templates: list[dict[str, Any]],
    errors: list[dict[str, Any]],
) -> None:
    actual = {(row["problemSlug"], row["languageSlug"]): row["code"] for row in templates}
    for slug, problem in sorted(problem_by_slug.items()):
        expected = problem.get("python3Template")
        if expected is None:
            continue
        code = actual.get((slug, "python3"))
        if code != expected:
            errors.append({"rule": "python3_template_mismatch", "problemSlug": slug})


def write_seed(output_dir: Path, result: dict[str, Any]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    for filename in OUTPUT_FILES:
        write_jsonl(output_dir / filename, result["outputRows"][filename])
    output_sha256 = {filename: sha256_file(output_dir / filename) for filename in OUTPUT_FILES}
    fetch_report = result["fetchReport"]
    manifest = {
        "queryVersion": METADATA_QUERY_VERSION,
        "sourceSnapshot": fetch_report["sourceSnapshot"],
        "sourceFetchedAt": fetch_report.get("completedAt"),
        "inputProblemsPath": result["problemSeedPath"],
        "inputProblemsSha256": result["problemSeedSha256"],
        "rawCacheFileCount": len(fetch_report.get("cacheChecksums", {})),
        "rawCacheChecksums": fetch_report.get("cacheChecksums", {}),
        # 路径相对 seed 目录保存，导入器可从仓库根目录或 Maven 模块目录启动。
        "fetchReportPath": os.path.relpath(
            Path(result["fetchReportPath"]).resolve(), output_dir.resolve()),
        "fetchReportSha256": sha256_file(Path(result["fetchReportPath"])) if result.get("fetchReportPath") else None,
        "sourceProblemSites": result["sourceProblemSites"],
        "outputSha256": output_sha256,
        "stats": result["stats"],
        "buildErrors": result["errors"],
        "buildWarnings": result["warnings"],
    }
    write_json(output_dir / MANIFEST_FILE, manifest)
    # 审核报告不能由生成器伪造；保留旧报告会使篡改/过期 seed 无法被导入。
    audit_path = output_dir / AUDIT_REPORT_FILE
    if audit_path.exists():
        audit_path.unlink()


def read_optional(path: Path) -> Any | None:
    return read_json(path) if path.exists() else None


def normal_slug(value: Any) -> str | None:
    if value is None:
        return None
    slug = str(value).strip()
    return slug or None


def non_empty(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


if __name__ == "__main__":
    main()
