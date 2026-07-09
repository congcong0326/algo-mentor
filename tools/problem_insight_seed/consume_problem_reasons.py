#!/usr/bin/env python3
"""初始化并领取题目推荐理由生成任务。"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    sys.path.append(str(Path(__file__).resolve().parents[2]))

from tools.problem_seed.leetcode_api import read_jsonl, utc_now_iso


DEFAULT_PROBLEM_SEED_PATH = Path("data/seed/problems.jsonl")
DEFAULT_TASK_FILE = Path("data/problem-insight-seed/problem_reason_tasks.json")
DEFAULT_BATCH_DIR = Path("data/problem-insight-seed/batches")

STATUS_PENDING = "PENDING"
STATUS_IN_PROGRESS = "IN_PROGRESS"
STATUS_DONE = "DONE"

TASK_FIELDS = (
    "slug",
    "frontendDisplayId",
    "titleEn",
    "titleZh",
    "difficulty",
    "tagLabelsEn",
    "tagLabelsZh",
    "contentStatus",
    "leetcodeUrl",
    "consumed",
    "status",
    "claimId",
    "claimedAt",
    "completedAt",
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Initialize or claim problem reason generation tasks.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    init_parser = subparsers.add_parser("init", help="Create problem reason task file from problems.jsonl.")
    init_parser.add_argument("--seed", default=str(DEFAULT_PROBLEM_SEED_PATH))
    init_parser.add_argument("--task-file", default=str(DEFAULT_TASK_FILE))
    init_parser.add_argument("--force", action="store_true", help="Overwrite existing task file.")

    claim_parser = subparsers.add_parser("claim", help="Claim pending problem reason tasks.")
    claim_parser.add_argument("--task-file", default=str(DEFAULT_TASK_FILE))
    claim_parser.add_argument("--limit", type=int, default=30)
    claim_parser.add_argument("--claim-id", default="")
    claim_parser.add_argument("--output", default="")

    args = parser.parse_args()
    try:
        if args.command == "init":
            tasks = init_tasks(Path(args.seed), Path(args.task_file), force=args.force)
            print({"taskCount": len(tasks), "taskFile": str(Path(args.task_file))})
        elif args.command == "claim":
            claim_id = args.claim_id or default_claim_id()
            output = Path(args.output) if args.output else DEFAULT_BATCH_DIR / f"{claim_id}-input.json"
            claimed = claim_tasks(Path(args.task_file), output, limit=args.limit, claim_id=claim_id)
            print({"claimedCount": len(claimed), "claimId": claim_id, "output": str(output)})
    except ValueError as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc


def init_tasks(seed_path: Path, task_file: Path, *, force: bool = False) -> list[dict[str, Any]]:
    if task_file.exists() and not force:
        raise ValueError(f"task file already exists: {task_file}; use --force to overwrite")
    tasks = build_tasks_from_seed(read_jsonl(seed_path))
    write_json_array(task_file, tasks)
    return tasks


def build_tasks_from_seed(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    tasks: list[dict[str, Any]] = []
    seen_slugs: set[str] = set()
    for row_index, row in enumerate(rows, start=1):
        slug = normalized_text(row.get("slug"))
        if slug is None:
            raise ValueError(f"missing slug in seed row {row_index}")
        if slug in seen_slugs:
            raise ValueError(f"duplicate slug in seed: {slug}")
        seen_slugs.add(slug)
        tasks.append({
            "slug": slug,
            "frontendDisplayId": optional_text(row.get("frontendDisplayId")),
            "titleEn": optional_text(row.get("titleEn")),
            "titleZh": optional_text(row.get("titleZh")),
            "difficulty": optional_text(row.get("difficulty")),
            "tagLabelsEn": string_list(row.get("tagLabelsEn"), "tagLabelsEn", slug),
            "tagLabelsZh": string_list(row.get("tagLabelsZh"), "tagLabelsZh", slug),
            "contentStatus": optional_text(row.get("contentStatus")),
            "leetcodeUrl": optional_text(row.get("leetcodeUrl")),
            "consumed": False,
            "status": STATUS_PENDING,
            "claimId": None,
            "claimedAt": None,
            "completedAt": None,
        })
    return tasks


def claim_tasks(
    task_file: Path,
    output: Path,
    *,
    limit: int = 30,
    claim_id: str,
    now: str | None = None,
) -> list[dict[str, Any]]:
    if limit <= 0:
        raise ValueError("--limit must be positive")
    claim_id = claim_id.strip()
    if not claim_id:
        raise ValueError("--claim-id must not be blank")

    tasks = read_json_array(task_file)
    validate_tasks(tasks)
    claimed_at = now or utc_now_iso()
    claimed: list[dict[str, Any]] = []

    for task in tasks:
        if len(claimed) >= limit:
            break
        if task["status"] == STATUS_PENDING and task["consumed"] is False:
            task["status"] = STATUS_IN_PROGRESS
            task["claimId"] = claim_id
            task["claimedAt"] = claimed_at
            claimed.append(ordered_task(task))

    write_json_array(task_file, [ordered_task(task) for task in tasks])
    write_json_array(output, claimed)
    return claimed


def validate_tasks(tasks: Any) -> None:
    if not isinstance(tasks, list):
        raise ValueError("task file must contain a JSON array")
    seen_slugs: set[str] = set()
    for index, task in enumerate(tasks, start=1):
        if not isinstance(task, dict):
            raise ValueError(f"task row {index} must be an object")
        unexpected = set(task.keys()) - set(TASK_FIELDS)
        missing = set(TASK_FIELDS) - set(task.keys())
        if unexpected or missing:
            raise ValueError(f"task row {index} has invalid fields; missing={sorted(missing)}, unexpected={sorted(unexpected)}")
        slug = normalized_text(task.get("slug"))
        if slug is None:
            raise ValueError(f"task row {index} missing slug")
        if slug in seen_slugs:
            raise ValueError(f"duplicate task slug: {slug}")
        seen_slugs.add(slug)
        if not isinstance(task.get("consumed"), bool):
            raise ValueError(f"task {slug} consumed must be boolean")
        if task.get("status") not in {STATUS_PENDING, STATUS_IN_PROGRESS, STATUS_DONE}:
            raise ValueError(f"task {slug} has invalid status: {task.get('status')}")


def read_json_array(path: Path) -> list[Any]:
    if not path.exists():
        raise ValueError(f"file does not exist: {path}")
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, list):
        raise ValueError(f"file must contain a JSON array: {path}")
    return value


def write_json_array(path: Path, value: list[Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def ordered_task(task: dict[str, Any]) -> dict[str, Any]:
    return {field: task.get(field) for field in TASK_FIELDS}


def default_claim_id() -> str:
    timestamp = utc_now_iso().replace("+00:00", "Z").replace(":", "").replace(".", "-")
    return f"claim-{timestamp}"


def normalized_text(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    stripped = value.strip()
    return stripped or None


def optional_text(value: Any) -> str | None:
    if value is None:
        return None
    return str(value).strip() or None


def string_list(value: Any, field: str, slug: str) -> list[str]:
    if value is None:
        return []
    if not isinstance(value, list):
        raise ValueError(f"problem {slug} field {field} must be a list")
    result: list[str] = []
    for item in value:
        text = optional_text(item)
        if text is None:
            raise ValueError(f"problem {slug} field {field} contains blank value")
        result.append(text)
    return result


if __name__ == "__main__":
    main()
