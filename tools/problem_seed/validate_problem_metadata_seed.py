#!/usr/bin/env python3
"""对已生成的题目学习元数据 seed 执行只读审核。"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    sys.path.append(str(Path(__file__).resolve().parents[2]))

from tools.problem_seed.leetcode_api import read_json, read_jsonl, write_json
from tools.problem_seed.problem_metadata_contract import (
    AUDIT_OUTCOME_PASSED,
    AUDIT_REPORT_FILE,
    CATEGORIES_FILE,
    CATEGORY_ITEMS_FILE,
    CATEGORY_TITLE_MAP,
    CODE_TEMPLATES_FILE,
    FETCH_REPORT_FILE,
    HINTS_FILE,
    MANIFEST_FILE,
    METADATA_SOURCE,
    OUTPUT_FILES,
    RELATION_TYPE_LEETCODE_SIMILAR,
    RELATIONS_FILE,
    SITE_TO_SOURCE_SITE,
)


DEFAULT_OUTPUT_DIR = Path("data/problem-metadata-seed")
DEFAULT_PROBLEM_SEED = Path("data/seed/problems.jsonl")


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate committed LeetCode learning metadata seed files.")
    parser.add_argument("--seed-dir", default=str(DEFAULT_OUTPUT_DIR))
    parser.add_argument("--problem-seed", default=str(DEFAULT_PROBLEM_SEED))
    parser.add_argument("--manual-sample-conclusion", default=None,
                        help="人工抽样结论；提供后写入报告并作为通过门禁的一部分。")
    parser.add_argument("--manual-sample-size", type=int, default=None)
    args = parser.parse_args()
    report = build_audit_report(
        Path(args.seed_dir), Path(args.problem_seed),
        manual_conclusion=args.manual_sample_conclusion,
        manual_sample_size=args.manual_sample_size,
    )
    write_json(Path(args.seed_dir) / AUDIT_REPORT_FILE, report)
    print(report["summary"])
    if report["outcome"] != AUDIT_OUTCOME_PASSED:
        raise SystemExit(1)


def build_audit_report(
    seed_dir: Path,
    problem_seed_path: Path,
    *,
    manual_conclusion: str | None = None,
    manual_sample_size: int | None = None,
) -> dict[str, Any]:
    manifest = read_json(seed_dir / MANIFEST_FILE)
    errors: list[dict[str, Any]] = []
    outputs = {filename: read_jsonl(seed_dir / filename) for filename in OUTPUT_FILES}
    validate_manifest(manifest, seed_dir, problem_seed_path, errors)
    known_problem_slugs = {str(row.get("slug")) for row in read_jsonl(problem_seed_path) if row.get("slug")}
    validate_relations(outputs[RELATIONS_FILE], known_problem_slugs, errors)
    validate_hints(outputs[HINTS_FILE], errors)
    validate_templates(outputs[CODE_TEMPLATES_FILE], problem_seed_path, errors)
    validate_categories(outputs[CATEGORIES_FILE], outputs[CATEGORY_ITEMS_FILE], errors)
    validate_fetch_report(manifest, seed_dir, errors)
    manual_sample = select_manual_sample(outputs, manifest, known_problem_slugs)
    existing_manual = {}
    audit_path = seed_dir / AUDIT_REPORT_FILE
    if audit_path.exists() and manual_sample_size is None and manual_conclusion is None:
        existing_manual = read_json(audit_path).get("manualSampling", {})
    effective_sample_size = (
        manual_sample_size if manual_sample_size is not None
        else existing_manual.get("sampleSize", 0)
    )
    effective_conclusion = (
        manual_conclusion if manual_conclusion is not None
        else existing_manual.get("conclusion")
    )
    report = {
        "outcome": AUDIT_OUTCOME_PASSED
        if not errors and effective_sample_size >= 20 and effective_conclusion else "FAILED",
        "sourceSnapshot": manifest.get("sourceSnapshot"),
        "manifestPath": str(seed_dir / MANIFEST_FILE),
        "manifestSha256": sha256_file(seed_dir / MANIFEST_FILE),
        "summary": build_summary(outputs, manifest, known_problem_slugs),
        "errors": sorted(errors, key=canonical_json),
        "manualSampling": {
            "sampleSize": effective_sample_size,
            "conclusion": effective_conclusion,
            "status": "PASSED" if effective_sample_size >= 20 and effective_conclusion else "PENDING",
            "requiredCoverage": ["hints", "dualSite", "unmatchedTarget", "multiLanguage", "allCategories"],
            "candidateProblemSlugs": manual_sample,
        },
    }
    return report


def validate_manifest(manifest: dict[str, Any], seed_dir: Path, problem_seed_path: Path, errors: list[dict[str, Any]]) -> None:
    if manifest.get("buildErrors"):
        errors.append({"rule": "seed_build_errors", "count": len(manifest["buildErrors"])})
    if manifest.get("inputProblemsSha256") != sha256_file(problem_seed_path):
        errors.append({"rule": "problem_seed_checksum_mismatch"})
    expected = manifest.get("outputSha256")
    if not isinstance(expected, dict):
        errors.append({"rule": "missing_output_checksums"})
        return
    for filename in OUTPUT_FILES:
        if expected.get(filename) != sha256_file(seed_dir / filename):
            errors.append({"rule": "output_checksum_mismatch", "file": filename})


def validate_relations(rows: list[dict[str, Any]], known: set[str], errors: list[dict[str, Any]]) -> None:
    seen: set[tuple[str, str, str, str]] = set()
    for row in rows:
        source = non_empty(row.get("sourceSlug"))
        target = non_empty(row.get("targetSlug"))
        key = (source or "", target or "", str(row.get("relationType")), str(row.get("source")))
        if not source or not target:
            errors.append({"rule": "relation_empty_slug", "row": row})
        if source == target:
            errors.append({"rule": "relation_self_reference", "sourceSlug": source})
        if key in seen:
            errors.append({"rule": "relation_duplicate", "key": key})
        seen.add(key)
        if row.get("relationType") != RELATION_TYPE_LEETCODE_SIMILAR or row.get("source") != METADATA_SOURCE:
            errors.append({"rule": "relation_contract_mismatch", "key": key})
    matched = sum(1 for row in rows if row.get("targetSlug") in known)
    if matched + (len(rows) - matched) != len(rows):
        errors.append({"rule": "relation_count_conservation"})


def validate_hints(rows: list[dict[str, Any]], errors: list[dict[str, Any]]) -> None:
    by_source: dict[tuple[str, str], list[int]] = defaultdict(list)
    for row in rows:
        slug = non_empty(row.get("problemSlug"))
        site = row.get("sourceSite")
        content = non_empty(row.get("contentMarkdown"))
        ordinal = row.get("ordinal")
        if not slug or site not in SITE_TO_SOURCE_SITE.values() or not isinstance(ordinal, int) or ordinal <= 0:
            errors.append({"rule": "invalid_hint_schema", "row": row})
        if not content or re.search(r"</?[A-Za-z][^>]*>", content):
            errors.append({"rule": "hint_empty_or_html", "problemSlug": slug, "ordinal": ordinal})
        by_source[(slug or "", str(site))].append(ordinal if isinstance(ordinal, int) else -1)
    for key, ordinals in by_source.items():
        if sorted(ordinals) != list(range(1, len(ordinals) + 1)):
            errors.append({"rule": "hint_ordinal_gap", "key": key})


def validate_templates(rows: list[dict[str, Any]], problem_seed_path: Path, errors: list[dict[str, Any]]) -> None:
    seen: set[tuple[str, str]] = set()
    python3 = {(str(row.get("problemSlug")), str(row.get("languageSlug"))): row.get("code") for row in rows}
    for row in rows:
        key = (str(row.get("problemSlug")), str(row.get("languageSlug")))
        if key in seen:
            errors.append({"rule": "template_duplicate", "key": key})
        seen.add(key)
        if not non_empty(row.get("problemSlug")) or not non_empty(row.get("languageSlug")) or not non_empty(row.get("languageLabel")) or not non_empty(row.get("code")):
            errors.append({"rule": "invalid_template_schema", "key": key})
    for problem in read_jsonl(problem_seed_path):
        expected = problem.get("python3Template")
        if expected is not None and python3.get((str(problem.get("slug")), "python3")) != expected:
            errors.append({"rule": "python3_template_mismatch", "problemSlug": problem.get("slug")})


def validate_categories(categories: list[dict[str, Any]], items: list[dict[str, Any]], errors: list[dict[str, Any]]) -> None:
    mapped = {value["slug"]: value for value in CATEGORY_TITLE_MAP.values()}
    catalog = set()
    for row in categories:
        slug = row.get("slug")
        if slug in catalog or slug not in mapped or any(row.get(key) != mapped[slug][key] for key in ("slug", "nameEn", "nameZh")):
            errors.append({"rule": "invalid_category_mapping", "row": row})
        catalog.add(slug)
    for row in items:
        if row.get("categorySlug") not in catalog or row.get("source") != METADATA_SOURCE:
            errors.append({"rule": "invalid_category_item", "row": row})


def validate_fetch_report(manifest: dict[str, Any], seed_dir: Path, errors: list[dict[str, Any]]) -> None:
    report_path = Path(str(manifest.get("fetchReportPath") or ""))
    if not report_path.is_absolute():
        report_path = seed_dir / report_path
    if not report_path.exists():
        errors.append({"rule": "missing_fetch_report"})
        return
    if manifest.get("fetchReportSha256") != sha256_file(report_path):
        errors.append({"rule": "fetch_report_checksum_mismatch"})
    report = read_json(report_path)
    if report.get("sourceSnapshot") != manifest.get("sourceSnapshot"):
        errors.append({"rule": "source_snapshot_mismatch"})
    states = report.get("sourceStates")
    if not isinstance(states, list):
        errors.append({"rule": "missing_source_states"})
        return
    terminal = {"fetched", "notAvailable", "failed"}
    slugs = [row.get("slug") for row in states]
    if len(slugs) != len(set(slugs)) or any(row.get("status") not in terminal for row in states):
        errors.append({"rule": "invalid_source_terminal_state"})
    status_counts = Counter(row.get("status") for row in states)
    report_summary = report.get("summary")
    if not isinstance(report_summary, dict):
        errors.append({"rule": "missing_fetch_summary"})
    else:
        if report_summary.get("sourceProblemCount") != len(states):
            errors.append({"rule": "fetch_source_count_mismatch"})
        if report_summary.get("sourceStatusCounts") != {
            status: count for status, count in sorted(status_counts.items())
        }:
            errors.append({"rule": "fetch_status_count_mismatch"})
        if report_summary.get("failedFetchCount") != status_counts["failed"]:
            errors.append({"rule": "fetch_failed_count_mismatch"})
        if report_summary.get("notAvailableCount") != status_counts["notAvailable"]:
            errors.append({"rule": "fetch_not_available_count_mismatch"})
    manifest_sources = {row.get("problemSlug") for row in manifest.get("sourceProblemSites", [])}
    if set(slugs) != manifest_sources:
        errors.append({"rule": "fetch_source_scope_mismatch"})
    if any(row.get("status") == "failed" for row in states):
        errors.append({"rule": "failed_fetch_present"})


def build_summary(
    outputs: dict[str, list[dict[str, Any]]],
    manifest: dict[str, Any],
    known_problem_slugs: set[str],
) -> dict[str, Any]:
    relations = outputs[RELATIONS_FILE]
    matched_targets = sum(1 for row in relations if row.get("targetSlug") in known_problem_slugs)
    problem_slugs = {row.get("problemSlug") for row in outputs[HINTS_FILE]}
    fetch_stats = manifest.get("stats", {})
    source_status_counts = fetch_stats.get("sourceStatusCounts", {})
    return {
        "relationCount": len(relations),
        "matchedTargetCount": matched_targets,
        "unmatchedTargetCount": len(relations) - matched_targets,
        "hintCount": len(outputs[HINTS_FILE]),
        "hintCountBySourceSite": dict(sorted(Counter(row.get("sourceSite") for row in outputs[HINTS_FILE]).items())),
        "codeTemplateCount": len(outputs[CODE_TEMPLATES_FILE]),
        "languageDistribution": dict(sorted(Counter(row.get("languageSlug") for row in outputs[CODE_TEMPLATES_FILE]).items())),
        "categoryDistribution": dict(sorted(Counter(row.get("categorySlug") for row in outputs[CATEGORY_ITEMS_FILE]).items())),
        "highOutDegree": high_out_degree(relations),
        "metadataSourceProblemCount": len(problem_slugs),
        "sourceStatusCounts": source_status_counts,
        "fetchedCount": fetch_stats.get("fetchedCount", source_status_counts.get("fetched", 0)),
        "notAvailableCount": fetch_stats.get("notAvailableCount", source_status_counts.get("notAvailable", 0)),
        "failedFetchCount": fetch_stats.get("failedFetchCount", source_status_counts.get("failed", 0)),
    }


def high_out_degree(relations: list[dict[str, Any]], limit: int = 20) -> list[dict[str, Any]]:
    counts = Counter(row.get("sourceSlug") for row in relations)
    return [{"problemSlug": slug, "outDegree": count} for slug, count in sorted(counts.items(), key=lambda item: (-item[1], item[0]))[:limit]]


def select_manual_sample(
    outputs: dict[str, list[dict[str, Any]]],
    manifest: dict[str, Any],
    known_problem_slugs: set[str],
) -> list[str]:
    """优先覆盖审核门禁，再以稳定 slug 顺序补足至少 20 个可人工查看的来源题。"""
    selected: set[str] = set()
    all_sources = sorted({str(row.get("problemSlug")) for row in manifest.get("sourceProblemSites", [])})
    hint_slugs = {str(row.get("problemSlug")) for row in outputs[HINTS_FILE] if row.get("problemSlug")}
    category_slugs = {
        category: next(
            (str(row["problemSlug"]) for row in outputs[CATEGORY_ITEMS_FILE]
             if row.get("categorySlug") == category),
            None,
        )
        for category in sorted({row.get("categorySlug") for row in outputs[CATEGORY_ITEMS_FILE]})
    }

    def add_first(predicate: Any) -> None:
        for slug in all_sources:
            if predicate(slug):
                selected.add(slug)
                return

    add_first(lambda slug: slug in hint_slugs)
    add_first(lambda slug: slug not in hint_slugs)
    add_first(lambda slug: any(
        row.get("sourceSlug") == slug and row.get("targetSlug") not in known_problem_slugs
        for row in outputs[RELATIONS_FILE]))
    language_counts = Counter(row.get("problemSlug") for row in outputs[CODE_TEMPLATES_FILE])
    add_first(lambda slug: language_counts.get(slug, 0) > 1)
    add_first(lambda slug: next((
        len(row.get("sourceSites", [])) > 1 for row in manifest.get("sourceProblemSites", [])
        if row.get("problemSlug") == slug), False))
    for slug in category_slugs.values():
        if slug:
            selected.add(slug)
    for slug in all_sources:
        if len(selected) >= 20:
            break
        selected.add(slug)
    return sorted(selected)


def non_empty(value: Any) -> str | None:
    if value is None:
        return None
    value = str(value).strip()
    return value or None


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


if __name__ == "__main__":
    main()
