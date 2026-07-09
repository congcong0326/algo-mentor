import json
import tempfile
import unittest
from pathlib import Path

from tools.problem_insight_seed import append_problem_reasons as append
from tools.problem_insight_seed import consume_problem_reasons as consume


def seed_row(slug: str) -> dict:
    return {
        "slug": slug,
        "frontendDisplayId": "1",
        "titleEn": "Two Sum",
        "titleZh": "两数之和",
        "difficulty": "EASY",
        "tagLabelsEn": ["Array"],
        "tagLabelsZh": ["数组"],
        "contentStatus": "BILINGUAL",
        "leetcodeUrl": f"https://leetcode.com/problems/{slug}/",
    }


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class AppendProblemReasonsTest(unittest.TestCase):

    def test_valid_result_appends_to_json_array_and_marks_task_done(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            task_file = root / "tasks.json"
            result_file = root / "problem_reasons.json"
            output = root / "batch-output.json"
            tasks = consume.build_tasks_from_seed([seed_row("two-sum")])
            consume.write_json_array(task_file, tasks)
            consume.claim_tasks(task_file, root / "batch-input.json", limit=30, claim_id="batch-001", now="2026-07-09T00:00:00+00:00")
            write_json(output, [{
                "slug": "two-sum",
                "reasonEN": "This anchors hash-table lookup practice around a compact easy problem.",
                "reasonZH": "这题适合用来建立哈希查找和一次遍历的基础模板。",
            }])

            appended = append.append_reasons(
                input_path=output,
                task_file=task_file,
                result_file=result_file,
                now="2026-07-09T00:02:00+00:00",
            )

            self.assertEqual(1, len(appended))
            results = json.loads(result_file.read_text(encoding="utf-8"))
            self.assertEqual([{
                "slug": "two-sum",
                "reasonEN": "This anchors hash-table lookup practice around a compact easy problem.",
                "reasonZH": "这题适合用来建立哈希查找和一次遍历的基础模板。",
            }], results)
            saved_tasks = json.loads(task_file.read_text(encoding="utf-8"))
            self.assertEqual(consume.STATUS_DONE, saved_tasks[0]["status"])
            self.assertTrue(saved_tasks[0]["consumed"])
            self.assertEqual("2026-07-09T00:02:00+00:00", saved_tasks[0]["completedAt"])

    def test_missing_reason_field_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            task_file = root / "tasks.json"
            output = root / "batch-output.json"
            consume.write_json_array(task_file, consume.build_tasks_from_seed([seed_row("two-sum")]))
            write_json(output, [{"slug": "two-sum", "reasonEN": "Only English."}])

            with self.assertRaisesRegex(ValueError, "reasonZH"):
                append.append_reasons(input_path=output, task_file=task_file, result_file=root / "reasons.json")

    def test_duplicate_existing_slug_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            task_file = root / "tasks.json"
            result_file = root / "problem_reasons.json"
            output = root / "batch-output.json"
            tasks = consume.build_tasks_from_seed([seed_row("two-sum")])
            consume.write_json_array(task_file, tasks)
            consume.claim_tasks(task_file, root / "batch-input.json", limit=30, claim_id="batch-001", now="2026-07-09T00:00:00+00:00")
            write_json(result_file, [{
                "slug": "two-sum",
                "reasonEN": "Existing reason.",
                "reasonZH": "已有理由。",
            }])
            write_json(output, [{
                "slug": "two-sum",
                "reasonEN": "New reason.",
                "reasonZH": "新理由。",
            }])

            with self.assertRaisesRegex(ValueError, "already exists"):
                append.append_reasons(input_path=output, task_file=task_file, result_file=result_file)

    def test_unclaimed_slug_fails(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            task_file = root / "tasks.json"
            output = root / "batch-output.json"
            consume.write_json_array(task_file, consume.build_tasks_from_seed([seed_row("two-sum")]))
            write_json(output, [{
                "slug": "two-sum",
                "reasonEN": "New reason.",
                "reasonZH": "新理由。",
            }])

            with self.assertRaisesRegex(ValueError, "not claimed"):
                append.append_reasons(input_path=output, task_file=task_file, result_file=root / "reasons.json")


if __name__ == "__main__":
    unittest.main()
