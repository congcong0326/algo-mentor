#!/usr/bin/env python3
"""追加题目推荐理由并标记生成任务完成。"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    sys.path.append(str(Path(__file__).resolve().parents[2]))

from tools.problem_insight_seed.consume_problem_reasons import (
    DEFAULT_TASK_FILE,
    STATUS_DONE,
    STATUS_IN_PROGRESS,
    TASK_FIELDS,
    ordered_task,
    read_json_array,
    utc_now_iso,
    write_json_array,
)


DEFAULT_RESULT_FILE = Path("data/problem-insight-seed/problem_reasons.json")
RESULT_FIELDS = ("slug", "reasonEN", "reasonZH")


def main() -> None:
    parser = argparse.ArgumentParser(description="Append generated problem reasons and mark claimed tasks done.")
    parser.add_argument("--input", required=True, help="JSON array containing slug, reasonEN, reasonZH.")
    parser.add_argument("--task-file", default=str(DEFAULT_TASK_FILE))
    parser.add_argument("--result-file", default=str(DEFAULT_RESULT_FILE))
    args = parser.parse_args()

    try:
        appended = append_reasons(
            input_path=Path(args.input),
            task_file=Path(args.task_file),
            result_file=Path(args.result_file),
        )
        print({"appendedCount": len(appended), "resultFile": str(Path(args.result_file))})
    except ValueError as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc


def append_reasons(
    *,
    input_path: Path,
    task_file: Path,
    result_file: Path,
    now: str | None = None,
) -> list[dict[str, Any]]:
    new_results = validate_new_results(read_json_array(input_path))
    tasks = read_json_array(task_file)
    validate_task_file(tasks)
    existing_results = read_existing_results(result_file)
    validate_appendable(new_results, existing_results, tasks)

    completed_at = now or utc_now_iso()
    task_by_slug = {str(task["slug"]): task for task in tasks}
    for result in new_results:
        task = task_by_slug[result["slug"]]
        task["status"] = STATUS_DONE
        task["consumed"] = True
        task["completedAt"] = completed_at

    write_json_array(result_file, [ordered_result(result) for result in existing_results + new_results])
    write_json_array(task_file, [ordered_task(task) for task in tasks])
    return new_results


def validate_new_results(value: Any) -> list[dict[str, str]]:
    if not isinstance(value, list):
        raise ValueError("input must contain a JSON array")
    if not value:
        raise ValueError("input must contain at least one result")
    results: list[dict[str, str]] = []
    seen_slugs: set[str] = set()
    for index, item in enumerate(value, start=1):
        result = validate_result_item(item, f"input row {index}")
        slug = result["slug"]
        if slug in seen_slugs:
            raise ValueError(f"duplicate slug in input: {slug}")
        seen_slugs.add(slug)
        results.append(result)
    return results


def read_existing_results(result_file: Path) -> list[dict[str, str]]:
    if not result_file.exists():
        return []
    value = read_json_array(result_file)
    results: list[dict[str, str]] = []
    seen_slugs: set[str] = set()
    for index, item in enumerate(value, start=1):
        result = validate_result_item(item, f"result row {index}")
        slug = result["slug"]
        if slug in seen_slugs:
            raise ValueError(f"duplicate slug in result file: {slug}")
        seen_slugs.add(slug)
        results.append(result)
    return results


def validate_result_item(item: Any, label: str) -> dict[str, str]:
    if not isinstance(item, dict):
        raise ValueError(f"{label} must be an object")
    missing = set(RESULT_FIELDS) - set(item.keys())
    unexpected = set(item.keys()) - set(RESULT_FIELDS)
    if missing or unexpected:
        raise ValueError(f"{label} has invalid fields; missing={sorted(missing)}, unexpected={sorted(unexpected)}")

    result: dict[str, str] = {}
    for field in RESULT_FIELDS:
        value = item.get(field)
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"{label} field {field} must be a non-empty string")
        result[field] = value.strip()
    return result


def validate_task_file(tasks: Any) -> None:
    if not isinstance(tasks, list):
        raise ValueError("task file must contain a JSON array")
    seen_slugs: set[str] = set()
    for index, task in enumerate(tasks, start=1):
        if not isinstance(task, dict):
            raise ValueError(f"task row {index} must be an object")
        missing = set(TASK_FIELDS) - set(task.keys())
        unexpected = set(task.keys()) - set(TASK_FIELDS)
        if missing or unexpected:
            raise ValueError(f"task row {index} has invalid fields; missing={sorted(missing)}, unexpected={sorted(unexpected)}")
        slug = task.get("slug")
        if not isinstance(slug, str) or not slug.strip():
            raise ValueError(f"task row {index} missing slug")
        if slug in seen_slugs:
            raise ValueError(f"duplicate task slug: {slug}")
        seen_slugs.add(slug)


def validate_appendable(
    new_results: list[dict[str, str]],
    existing_results: list[dict[str, str]],
    tasks: list[dict[str, Any]],
) -> None:
    task_by_slug = {str(task["slug"]): task for task in tasks}
    existing_slugs = {result["slug"] for result in existing_results}
    for result in new_results:
        slug = result["slug"]
        if slug in existing_slugs:
            raise ValueError(f"result already exists for slug: {slug}")
        task = task_by_slug.get(slug)
        if task is None:
            raise ValueError(f"slug not found in task file: {slug}")
        if task.get("status") != STATUS_IN_PROGRESS:
            raise ValueError(f"slug is not claimed: {slug}")
        if task.get("consumed") is not False:
            raise ValueError(f"slug is already consumed: {slug}")
        if not task.get("claimId") or not task.get("claimedAt"):
            raise ValueError(f"slug is missing claim metadata: {slug}")
        if task.get("completedAt") is not None:
            raise ValueError(f"slug is already completed: {slug}")


def ordered_result(result: dict[str, str]) -> dict[str, str]:
    return {field: result[field] for field in RESULT_FIELDS}


if __name__ == "__main__":
    main()
