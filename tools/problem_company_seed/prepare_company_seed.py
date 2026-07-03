#!/usr/bin/env python3
"""生成并校验公司维度题目元数据 seed。"""

from __future__ import annotations

import argparse
import csv
import re
import subprocess
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Iterable
from urllib.parse import unquote, urlparse

if __package__ in (None, ""):
    sys.path.append(str(Path(__file__).resolve().parents[2]))

from tools.problem_seed.leetcode_api import read_jsonl, utc_now_iso, write_json, write_jsonl
from tools.problem_seed.prepare_seed import normalize_slug


SOURCE_LIQUIDSLR_NAME = "liquidslr/leetcode-company-wise-problems"
SOURCE_LIQUIDSLR_URL = "https://github.com/liquidslr/leetcode-company-wise-problems"
SOURCE_LEETCODE_TOP_NAME = "afatcoder/LeetcodeTop"
SOURCE_LEETCODE_TOP_URL = "https://github.com/afatcoder/LeetcodeTop"

DEFAULT_SOURCE_ROOT = Path("data/sources/company-metadata")
DEFAULT_OUTPUT_DIR = Path("data/company-seed")
DEFAULT_LOCAL_PROBLEMS_PATH = Path("data/seed/problems.jsonl")
DEFAULT_REPORT_PATH = Path("data/reports/problem_company_seed_report.json")

ROLE_BY_STEM = {
    "backend": "BACKEND",
    "后端": "BACKEND",
    "frontend": "FRONTEND",
    "前端": "FRONTEND",
    "algorithm": "ALGORITHM",
    "算法": "ALGORITHM",
    "client": "CLIENT",
    "客户端": "CLIENT",
    "test": "TEST",
    "测试": "TEST",
    "data": "DATA",
    "数据": "DATA",
    "sde": "GENERAL",
    "汇总": "GENERAL",
    "all": "GENERAL",
    "general": "GENERAL",
}

RECENCY_BUCKET_BY_LABEL = {
    "thirty-days": "THIRTY_DAYS",
    "three-months": "THREE_MONTHS",
    "six-months": "SIX_MONTHS",
    "more-than-six-months": "MORE_THAN_SIX_MONTHS",
    "all": "ALL_TIME",
}

COMPANY_NAME_ALIASES = {
    "alibaba": "阿里巴巴",
    "baidu": "百度",
    "bytedance": "字节跳动",
    "kuaishou": "快手",
    "meituan": "美团",
    "tencent": "腾讯",
    "yuanfudao": "猿辅导",
}

CHINA_COMPANY_SLUGS = {
    "alibaba",
    "baidu",
    "bytedance",
    "kuaishou",
    "meituan",
    "tencent",
    "yuanfudao",
}

COMPANY_MARKET_CHINA = "CHINA"
COMPANY_MARKET_OTHER = "OTHER"


@dataclass(frozen=True)
class CompanySignal:
    company_slug: str
    company_name: str
    company_market: str
    role: str
    problem_slug: str
    recency_bucket: str
    frequency_score: Decimal | None
    rank: int | None
    acceptance_rate: Decimal | None
    source_name: str
    source_url: str
    source_commit: str | None
    source_problem_url: str
    source_order: int


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate company problem metadata seed.")
    parser.add_argument("--liquidslr-dir", default="")
    parser.add_argument("--leetcode-top-dir", default="")
    parser.add_argument("--source-root", default=str(DEFAULT_SOURCE_ROOT))
    parser.add_argument("--local-problems", default=str(DEFAULT_LOCAL_PROBLEMS_PATH))
    parser.add_argument("--problem-seed", default="", help="Deprecated alias for --local-problems.")
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    parser.add_argument("--report", default=str(DEFAULT_REPORT_PATH))
    parser.add_argument("--fetch", action="store_true", help="Clone missing source repositories into --source-root.")
    parser.add_argument("--update-sources", action="store_true", help="Run git pull --ff-only for existing source repositories.")
    args = parser.parse_args()

    source_root = Path(args.source_root)
    liquidslr_dir = Path(args.liquidslr_dir) if args.liquidslr_dir else source_root / "leetcode-company-wise-problems"
    leetcode_top_dir = Path(args.leetcode_top_dir) if args.leetcode_top_dir else source_root / "LeetcodeTop"

    if args.fetch:
        ensure_git_source(SOURCE_LIQUIDSLR_URL, liquidslr_dir, args.update_sources)
        ensure_git_source(SOURCE_LEETCODE_TOP_URL, leetcode_top_dir, args.update_sources)

    local_problems_path = Path(args.problem_seed) if args.problem_seed else Path(args.local_problems)
    seed, report = build_company_seed(
        liquidslr_dir=liquidslr_dir,
        leetcode_top_dir=leetcode_top_dir,
        local_problems_path=local_problems_path,
    )
    write_company_seed(Path(args.output_dir), seed, report)
    write_json(Path(args.report), report)
    print({
        "signals": report["signalCount"],
        "matched": report["matchedSignalCount"],
        "unmatchedProblems": report["unmatchedProblemCount"],
        "duplicates": report["duplicateSignalCount"],
        "errors": report["errorCount"],
    })


def build_company_seed(
    *,
    liquidslr_dir: Path,
    leetcode_top_dir: Path,
    local_problems_path: Path,
) -> tuple[list[CompanySignal], dict[str, Any]]:
    source_reports: list[dict[str, Any]] = []
    errors: list[dict[str, Any]] = []
    signals: list[CompanySignal] = []

    if liquidslr_dir.exists():
        parsed, source_errors = parse_liquidslr_source(liquidslr_dir)
        signals.extend(parsed)
        errors.extend(source_errors)
        source_reports.append(source_summary(SOURCE_LIQUIDSLR_NAME, SOURCE_LIQUIDSLR_URL, liquidslr_dir, parsed, source_errors))
    else:
        errors.append({"rule": "missing_source_dir", "sourceName": SOURCE_LIQUIDSLR_NAME, "path": str(liquidslr_dir)})

    if leetcode_top_dir.exists():
        parsed, source_errors = parse_leetcode_top_source(leetcode_top_dir)
        signals.extend(parsed)
        errors.extend(source_errors)
        source_reports.append(source_summary(SOURCE_LEETCODE_TOP_NAME, SOURCE_LEETCODE_TOP_URL, leetcode_top_dir, parsed, source_errors))
    else:
        errors.append({"rule": "missing_source_dir", "sourceName": SOURCE_LEETCODE_TOP_NAME, "path": str(leetcode_top_dir)})

    local_slugs = load_local_problem_slugs(local_problems_path)
    deduped, duplicate_count = dedupe_signals(signals)
    unmatched = [signal for signal in deduped if signal.problem_slug not in local_slugs]
    matched = [signal for signal in deduped if signal.problem_slug in local_slugs]
    ranked = assign_ranks(matched)
    report = build_report(
        ranked,
        unmatched,
        errors,
        duplicate_count,
        len(deduped) + duplicate_count,
        local_slugs,
        source_reports,
        local_problems_path,
    )
    return ranked, report


def parse_liquidslr_source(source_dir: Path) -> tuple[list[CompanySignal], list[dict[str, Any]]]:
    signals: list[CompanySignal] = []
    errors: list[dict[str, Any]] = []
    source_commit = source_commit_hash(source_dir)
    order = 0

    for csv_path in sorted(source_dir.glob("*/*.csv")):
        company_slug = company_slug_from_name(csv_path.parent.name)
        if not company_slug:
            errors.append(error("invalid_company_slug", SOURCE_LIQUIDSLR_NAME, csv_path, company=csv_path.parent.name))
            continue
        recency_bucket = recency_bucket_from_filename(csv_path)
        if recency_bucket is None:
            errors.append(error("unknown_recency_bucket", SOURCE_LIQUIDSLR_NAME, csv_path))
            continue
        try:
            with csv_path.open(encoding="utf-8-sig", newline="") as file:
                reader = csv.DictReader(file)
                for row_index, row in enumerate(reader, start=2):
                    order += 1
                    row_signal, row_error = parse_liquidslr_row(
                        row,
                        csv_path,
                        row_index,
                        company_slug,
                        display_company_name(csv_path.parent.name),
                        recency_bucket,
                        source_commit,
                        order,
                    )
                    if row_signal:
                        signals.append(row_signal)
                    if row_error:
                        errors.append(row_error)
        except UnicodeDecodeError as exc:
            errors.append(error("csv_decode_error", SOURCE_LIQUIDSLR_NAME, csv_path, message=str(exc)))
    return signals, errors


def parse_liquidslr_row(
    row: dict[str, str],
    path: Path,
    row_index: int,
    company_slug: str,
    company_name: str,
    recency_bucket: str,
    source_commit: str | None,
    order: int,
) -> tuple[CompanySignal | None, dict[str, Any] | None]:
    link = first_non_empty(row.get("Link"), row.get("link"), row.get("URL"), row.get("Url"))
    problem_slug = extract_problem_slug(str(link or ""))
    if problem_slug is None:
        return None, error("non_problem_link", SOURCE_LIQUIDSLR_NAME, path, row=row_index, value=link)

    frequency, frequency_error = parse_decimal(first_non_empty(row.get("Frequency"), row.get("frequency")))
    if frequency_error:
        return None, error("invalid_frequency", SOURCE_LIQUIDSLR_NAME, path, row=row_index, value=row.get("Frequency"))

    acceptance_rate, acceptance_error = parse_acceptance_rate(first_non_empty(row.get("Acceptance Rate"), row.get("AcceptanceRate")))
    if acceptance_error:
        return None, error("invalid_acceptance_rate", SOURCE_LIQUIDSLR_NAME, path, row=row_index, value=row.get("Acceptance Rate"))

    return CompanySignal(
        company_slug=company_slug,
        company_name=company_name,
        company_market=company_market(company_slug),
        role="GENERAL",
        problem_slug=problem_slug,
        recency_bucket=recency_bucket,
        frequency_score=frequency,
        rank=None,
        acceptance_rate=acceptance_rate,
        source_name=SOURCE_LIQUIDSLR_NAME,
        source_url=SOURCE_LIQUIDSLR_URL,
        source_commit=source_commit,
        source_problem_url=str(link),
        source_order=order,
    ), None


def parse_leetcode_top_source(source_dir: Path) -> tuple[list[CompanySignal], list[dict[str, Any]]]:
    signals: list[CompanySignal] = []
    errors: list[dict[str, Any]] = []
    source_commit = source_commit_hash(source_dir)
    order = 0

    for markdown_path in sorted(source_dir.glob("*/*.md")):
        company_slug = company_slug_from_name(markdown_path.parent.name)
        if not company_slug:
            errors.append(error("invalid_company_slug", SOURCE_LEETCODE_TOP_NAME, markdown_path, company=markdown_path.parent.name))
            continue
        role = role_from_filename(markdown_path)
        if role is None:
            errors.append(error("unknown_role", SOURCE_LEETCODE_TOP_NAME, markdown_path, value=markdown_path.stem))
            continue
        rows = parse_markdown_table_rows(markdown_path.read_text(encoding="utf-8"))
        for row_index, row in rows:
            order += 1
            row_signal, row_error = parse_leetcode_top_row(
                row,
                markdown_path,
                row_index,
                company_slug,
                display_company_name(markdown_path.parent.name),
                role,
                source_commit,
                order,
            )
            if row_signal:
                signals.append(row_signal)
            if row_error:
                errors.append(row_error)
    return signals, errors


def parse_leetcode_top_row(
    row: list[str],
    path: Path,
    row_index: int,
    company_slug: str,
    company_name: str,
    role: str,
    source_commit: str | None,
    order: int,
) -> tuple[CompanySignal | None, dict[str, Any] | None]:
    if len(row) >= 5 and extract_first_url(row[3]):
        frequency_value = row[4]
        link_value = row[3]
    elif len(row) >= 3:
        frequency_value = row[1]
        link_value = row[2]
    else:
        return None, error("missing_problem_link", SOURCE_LEETCODE_TOP_NAME, path, row=row_index, value=" | ".join(row))

    frequency, frequency_error = parse_decimal(frequency_value)
    if frequency_error:
        return None, error("invalid_frequency", SOURCE_LEETCODE_TOP_NAME, path, row=row_index, value=frequency_value)

    source_problem_url = extract_first_url(link_value) or link_value
    problem_slug = extract_problem_slug(source_problem_url)
    if problem_slug is None:
        return None, error("non_problem_link", SOURCE_LEETCODE_TOP_NAME, path, row=row_index, value=link_value)

    return CompanySignal(
        company_slug=company_slug,
        company_name=company_name,
        company_market=company_market(company_slug),
        role=role,
        problem_slug=problem_slug,
        recency_bucket="ALL_TIME",
        frequency_score=frequency,
        rank=None,
        acceptance_rate=None,
        source_name=SOURCE_LEETCODE_TOP_NAME,
        source_url=SOURCE_LEETCODE_TOP_URL,
        source_commit=source_commit,
        source_problem_url=source_problem_url,
        source_order=order,
    ), None


def parse_markdown_table_rows(markdown: str) -> list[tuple[int, list[str]]]:
    rows: list[tuple[int, list[str]]] = []
    for line_number, line in enumerate(markdown.splitlines(), start=1):
        if "|" not in line:
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) < 2 or is_markdown_separator(cells):
            continue
        normalized = "".join(cells)
        if (
            "题目" in normalized
            and ("出现次数" in normalized or "频率" in normalized or "频度" in normalized)
            and ("链接" in normalized or "地址" in normalized)
        ):
            continue
        if "算法题" in normalized and "次数" in normalized:
            continue
        rows.append((line_number, cells))
    return rows


def dedupe_signals(signals: Iterable[CompanySignal]) -> tuple[list[CompanySignal], int]:
    kept: dict[tuple[str, str, str, str, str], CompanySignal] = {}
    duplicate_count = 0
    for signal in signals:
        key = (signal.source_name, signal.company_slug, signal.role, signal.recency_bucket, signal.problem_slug)
        existing = kept.get(key)
        if existing is None:
            kept[key] = signal
            continue
        duplicate_count += 1
        if should_replace_signal(existing, signal):
            kept[key] = signal
    return list(kept.values()), duplicate_count


def assign_ranks(signals: Iterable[CompanySignal]) -> list[CompanySignal]:
    groups: dict[tuple[str, str, str, str], list[CompanySignal]] = defaultdict(list)
    for signal in signals:
        groups[(signal.source_name, signal.company_slug, signal.role, signal.recency_bucket)].append(signal)

    ranked: list[CompanySignal] = []
    for group_signals in groups.values():
        group_signals.sort(key=signal_sort_key)
        for index, signal in enumerate(group_signals, start=1):
            ranked.append(CompanySignal(
                company_slug=signal.company_slug,
                company_name=signal.company_name,
                company_market=signal.company_market,
                role=signal.role,
                problem_slug=signal.problem_slug,
                recency_bucket=signal.recency_bucket,
                frequency_score=signal.frequency_score,
                rank=index,
                acceptance_rate=signal.acceptance_rate,
                source_name=signal.source_name,
                source_url=signal.source_url,
                source_commit=signal.source_commit,
                source_problem_url=signal.source_problem_url,
                source_order=signal.source_order,
            ))
    ranked.sort(key=lambda item: (item.source_name, item.company_slug, item.role, item.recency_bucket, item.rank or 0, item.problem_slug))
    return ranked


def build_report(
    signals: list[CompanySignal],
    unmatched: list[CompanySignal],
    errors: list[dict[str, Any]],
    duplicate_count: int,
    source_signal_count: int,
    local_slugs: set[str],
    source_reports: list[dict[str, Any]],
    local_problems_path: Path,
) -> dict[str, Any]:
    company_count = len({signal.company_slug for signal in signals})
    market_by_company = {signal.company_slug: signal.company_market for signal in signals}
    roles = sorted({signal.role for signal in signals})
    recency_buckets = sorted({signal.recency_bucket for signal in signals})
    by_source = Counter(signal.source_name for signal in signals)
    by_company = Counter(signal.company_slug for signal in signals)
    unmatched_by_source = Counter(signal.source_name for signal in unmatched)
    unmatched_by_company = Counter(signal.company_slug for signal in unmatched)

    return {
        "generatedAt": utc_now_iso(),
        "localProblemsPath": str(local_problems_path),
        "localProblemCount": len(local_slugs),
        "sourceSignalCount": source_signal_count,
        "signalCount": len(signals),
        "matchedSignalCount": len(signals),
        "unmatchedSignalCount": len(unmatched),
        "unmatchedProblemCount": len({signal.problem_slug for signal in unmatched}),
        "duplicateSignalCount": duplicate_count,
        "errorCount": len(errors),
        "companyCount": company_count,
        "companyMarkets": dict(sorted(Counter(market_by_company.values()).items())),
        "roles": roles,
        "recencyBuckets": recency_buckets,
        "signalsBySource": dict(sorted(by_source.items())),
        "signalsByCompany": dict(sorted(by_company.items())),
        "unmatchedBySource": dict(sorted(unmatched_by_source.items())),
        "unmatchedByCompany": dict(sorted(unmatched_by_company.items())),
        "sources": source_reports,
        "unmatchedProblems": [signal_to_manifest_dict(signal) for signal in unmatched[:500]],
        "errors": errors[:500],
    }


def write_company_seed(output_dir: Path, signals: list[CompanySignal], report: dict[str, Any]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    write_jsonl(output_dir / "problem_company_signals.jsonl", [signal_to_seed_dict(signal) for signal in signals])
    manifest = {
        "generatedAt": report["generatedAt"],
        "signalCount": report["signalCount"],
        "matchedSignalCount": report["matchedSignalCount"],
        "unmatchedProblemCount": report["unmatchedProblemCount"],
        "duplicateSignalCount": report["duplicateSignalCount"],
        "errorCount": report["errorCount"],
        "files": ["problem_company_signals.jsonl"],
        "sources": report["sources"],
    }
    write_json(output_dir / "problem_company_seed_manifest.json", manifest)


def load_local_problem_slugs(problem_seed_path: Path) -> set[str]:
    if not problem_seed_path.exists():
        return set()
    slugs: set[str] = set()
    for row in read_jsonl(problem_seed_path):
        slug = row.get("slug")
        if isinstance(slug, str) and slug.strip():
            slugs.add(slug.strip())
    return slugs


def ensure_git_source(repo_url: str, target_dir: Path, update: bool) -> None:
    if not target_dir.exists():
        target_dir.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(["git", "clone", "--depth", "1", repo_url, str(target_dir)], check=True)
        return
    if update:
        subprocess.run(["git", "-C", str(target_dir), "pull", "--ff-only"], check=True)


def source_summary(
    source_name: str,
    source_url: str,
    source_dir: Path,
    signals: list[CompanySignal],
    errors: list[dict[str, Any]],
) -> dict[str, Any]:
    return {
        "sourceName": source_name,
        "sourceRepoUrl": source_url,
        "sourceCommit": source_commit_hash(source_dir),
        "sourceDir": str(source_dir),
        "signalCount": len(signals),
        "errorCount": len(errors),
        "companyCount": len({signal.company_slug for signal in signals}),
    }


def signal_to_seed_dict(signal: CompanySignal) -> dict[str, Any]:
    return {
        "companySlug": signal.company_slug,
        "companyName": signal.company_name,
        "companyMarket": signal.company_market,
        "role": signal.role,
        "problemSlug": signal.problem_slug,
        "recencyBucket": signal.recency_bucket,
        "frequencyScore": decimal_to_number(signal.frequency_score),
        "rank": signal.rank,
        "acceptanceRate": decimal_to_number(signal.acceptance_rate),
        "sourceName": signal.source_name,
        "sourceUrl": signal.source_url,
        "sourceCommit": signal.source_commit,
        "sourceProblemUrl": signal.source_problem_url,
    }


def signal_to_manifest_dict(signal: CompanySignal) -> dict[str, Any]:
    return {
        "sourceName": signal.source_name,
        "companySlug": signal.company_slug,
        "companyName": signal.company_name,
        "companyMarket": signal.company_market,
        "role": signal.role,
        "recencyBucket": signal.recency_bucket,
        "problemSlug": signal.problem_slug,
        "sourceProblemUrl": signal.source_problem_url,
    }


def should_replace_signal(existing: CompanySignal, candidate: CompanySignal) -> bool:
    existing_score = existing.frequency_score if existing.frequency_score is not None else Decimal("-1")
    candidate_score = candidate.frequency_score if candidate.frequency_score is not None else Decimal("-1")
    if candidate_score != existing_score:
        return candidate_score > existing_score
    return candidate.source_order < existing.source_order


def signal_sort_key(signal: CompanySignal) -> tuple[Decimal, int, str]:
    score = signal.frequency_score if signal.frequency_score is not None else Decimal("-1")
    return (-score, signal.source_order, signal.problem_slug)


def extract_problem_slug(url: str) -> str | None:
    parsed = urlparse(url.strip())
    if parsed.scheme not in {"http", "https"}:
        return None
    host = parsed.netloc.lower()
    if not (host.endswith("leetcode.com") or host.endswith("leetcode.cn") or host.endswith("leetcode-cn.com")):
        return None
    parts = [part for part in parsed.path.split("/") if part]
    if len(parts) < 2 or parts[0] != "problems":
        return None
    slug = unquote(parts[1]).strip()
    return slug or None


def extract_first_url(value: str) -> str | None:
    match = re.search(r"https?://[^\s)>]+", value)
    return match.group(0).rstrip(".,，。") if match else None


def company_slug_from_name(name: str) -> str | None:
    return normalize_slug(name)


def display_company_name(name: str) -> str:
    slug = company_slug_from_name(name)
    if slug and slug in COMPANY_NAME_ALIASES:
        return COMPANY_NAME_ALIASES[slug]
    return name.strip().replace("_", " ").replace("-", " ")


def company_market(company_slug: str) -> str:
    return COMPANY_MARKET_CHINA if company_slug in CHINA_COMPANY_SLUGS else COMPANY_MARKET_OTHER


def role_from_filename(path: Path) -> str | None:
    key = normalize_label(path.stem)
    return ROLE_BY_STEM.get(key)


def recency_bucket_from_filename(path: Path) -> str | None:
    label = re.sub(r"^\s*\d+\.\s*", "", path.stem)
    return RECENCY_BUCKET_BY_LABEL.get(normalize_label(label))


def parse_decimal(value: Any) -> tuple[Decimal | None, bool]:
    if value is None:
        return None, False
    text = str(value).strip()
    if not text:
        return None, False
    try:
        return Decimal(text.rstrip("%").replace(",", "")), False
    except InvalidOperation:
        return None, True


def parse_acceptance_rate(value: Any) -> tuple[Decimal | None, bool]:
    if value is None or not str(value).strip():
        return None, False
    raw = str(value).strip()
    parsed, has_error = parse_decimal(raw)
    if parsed is None or has_error:
        return None, True
    if raw.endswith("%"):
        return parsed / Decimal("100"), False
    return parsed, False


def decimal_to_number(value: Decimal | None) -> int | float | None:
    if value is None:
        return None
    if value == value.to_integral_value():
        return int(value)
    return float(value)


def normalize_label(value: str) -> str:
    return re.sub(r"[^a-z0-9\u4e00-\u9fff]+", "-", value.strip().lower()).strip("-")


def is_markdown_separator(cells: list[str]) -> bool:
    return all(re.fullmatch(r":?-+:?", cell.strip()) for cell in cells)


def first_non_empty(*values: Any) -> Any:
    for value in values:
        if value is None:
            continue
        if isinstance(value, str) and not value.strip():
            continue
        return value
    return None


def error(rule: str, source_name: str, path: Path, **kwargs: Any) -> dict[str, Any]:
    return {"rule": rule, "sourceName": source_name, "path": str(path), **kwargs}


def source_commit_hash(source_dir: Path) -> str | None:
    try:
        result = subprocess.run(
            ["git", "-C", str(source_dir), "rev-parse", "HEAD"],
            check=True,
            capture_output=True,
            text=True,
        )
        return result.stdout.strip() or None
    except (FileNotFoundError, subprocess.CalledProcessError):
        return None


if __name__ == "__main__":
    main()
