import json
import tempfile
import unittest
from pathlib import Path

from tools.problem_insight_seed import consume_problem_reasons as consume


def seed_row(slug: str, display_id: str = "1") -> dict:
    return {
        "slug": slug,
        "frontendDisplayId": display_id,
        "titleEn": "Two Sum",
        "titleZh": "两数之和",
        "difficulty": "EASY",
        "tagLabelsEn": ["Array", "Hash Table"],
        "tagLabelsZh": ["数组", "哈希表"],
        "contentStatus": "BILINGUAL",
        "leetcodeUrl": f"https://leetcode.com/problems/{slug}/",
    }


class ConsumeProblemReasonsTest(unittest.TestCase):

    def test_init_generates_json_array_task_file_from_jsonl_seed(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            seed_path = root / "problems.jsonl"
            task_file = root / "problem_reason_tasks.json"
            seed_path.write_text(
                json.dumps(seed_row("two-sum"), ensure_ascii=False) + "\n"
                + json.dumps(seed_row("add-two-numbers", "2"), ensure_ascii=False) + "\n",
                encoding="utf-8",
            )

            tasks = consume.init_tasks(seed_path, task_file)

            self.assertEqual(2, len(tasks))
            written = json.loads(task_file.read_text(encoding="utf-8"))
            self.assertEqual(2, len(written))
            self.assertEqual(list(consume.TASK_FIELDS), list(written[0].keys()))
            self.assertEqual("two-sum", written[0]["slug"])
            self.assertFalse(written[0]["consumed"])
            self.assertEqual(consume.STATUS_PENDING, written[0]["status"])
            self.assertIsNone(written[0]["claimId"])
            self.assertEqual(["Array", "Hash Table"], written[0]["tagLabelsEn"])

    def test_claim_limit_only_claims_pending_unconsumed_tasks(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            task_file = root / "tasks.json"
            output = root / "batch.json"
            tasks = consume.build_tasks_from_seed([seed_row("pending-a"), seed_row("done"), seed_row("claimed"), seed_row("pending-b")])
            tasks[1]["status"] = consume.STATUS_DONE
            tasks[1]["consumed"] = True
            tasks[1]["completedAt"] = "2026-01-01T00:00:00+00:00"
            tasks[2]["status"] = consume.STATUS_IN_PROGRESS
            tasks[2]["claimId"] = "existing"
            tasks[2]["claimedAt"] = "2026-01-01T00:00:00+00:00"
            consume.write_json_array(task_file, tasks)

            claimed = consume.claim_tasks(task_file, output, limit=30, claim_id="batch-001", now="2026-07-09T00:00:00+00:00")

            self.assertEqual(["pending-a", "pending-b"], [task["slug"] for task in claimed])
            saved = json.loads(task_file.read_text(encoding="utf-8"))
            self.assertEqual(consume.STATUS_IN_PROGRESS, saved[0]["status"])
            self.assertEqual("batch-001", saved[0]["claimId"])
            self.assertEqual(consume.STATUS_DONE, saved[1]["status"])
            self.assertEqual(consume.STATUS_IN_PROGRESS, saved[2]["status"])
            batch = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(["pending-a", "pending-b"], [task["slug"] for task in batch])

    def test_repeated_claim_does_not_return_same_slug(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            task_file = root / "tasks.json"
            tasks = consume.build_tasks_from_seed([seed_row(f"problem-{index}", str(index)) for index in range(40)])
            consume.write_json_array(task_file, tasks)

            first = consume.claim_tasks(task_file, root / "first.json", limit=30, claim_id="batch-001", now="2026-07-09T00:00:00+00:00")
            second = consume.claim_tasks(task_file, root / "second.json", limit=30, claim_id="batch-002", now="2026-07-09T00:01:00+00:00")

            first_slugs = {task["slug"] for task in first}
            second_slugs = {task["slug"] for task in second}
            self.assertEqual(30, len(first_slugs))
            self.assertEqual(10, len(second_slugs))
            self.assertTrue(first_slugs.isdisjoint(second_slugs))


if __name__ == "__main__":
    unittest.main()
