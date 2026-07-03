import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools.problem_seed import fetch_problem_index


def index_response(items: list[dict]) -> dict:
    return {"stat_status_pairs": items}


def index_item(slug: str, frontend_id: int, level: int, paid_only: bool = False) -> dict:
    return {
        "stat": {
            "question__title_slug": slug,
            "frontend_question_id": frontend_id,
            "question__title": slug.replace("-", " ").title(),
        },
        "difficulty": {"level": level},
        "paid_only": paid_only,
    }


class FetchProblemIndexTest(unittest.TestCase):

    def test_parse_index_reads_required_fields(self) -> None:
        rows = fetch_problem_index.parse_index(
            index_response([index_item("two-sum", 1, 1, True)]),
            "com",
        )

        self.assertEqual([{
            "slug": "two-sum",
            "frontendId": 1,
            "difficulty": "EASY",
            "paidOnly": True,
            "onCom": True,
            "onCn": False,
        }], rows)

    def test_parse_index_preserves_cn_only_slug_and_frontend_id(self) -> None:
        rows = fetch_problem_index.parse_index(
            index_response([index_item("cnHoX6", "LCP 82", 3)]),
            "cn",
        )

        self.assertEqual("cnHoX6", rows[0]["slug"])
        self.assertEqual("LCP 82", rows[0]["frontendId"])
        self.assertEqual("HARD", rows[0]["difficulty"])

    def test_merge_indexes_builds_union_by_slug(self) -> None:
        com_rows = [
            {"slug": "two-sum", "frontendId": 1, "difficulty": "EASY", "paidOnly": False, "onCom": True, "onCn": False},
            {"slug": "com-only", "frontendId": 2, "difficulty": "MEDIUM", "paidOnly": True, "onCom": True, "onCn": False},
        ]
        cn_rows = [
            {"slug": "two-sum", "frontendId": 1, "difficulty": "EASY", "paidOnly": False, "onCom": False, "onCn": True},
            {"slug": "cn-only", "frontendId": 3, "difficulty": "HARD", "paidOnly": False, "onCom": False, "onCn": True},
        ]

        rows = fetch_problem_index.merge_indexes(com_rows, cn_rows)

        by_slug = {row["slug"]: row for row in rows}
        self.assertEqual(3, len(rows))
        self.assertTrue(by_slug["two-sum"]["onCom"])
        self.assertTrue(by_slug["two-sum"]["onCn"])
        self.assertTrue(by_slug["com-only"]["paidOnly"])
        self.assertEqual(3, by_slug["cn-only"]["frontendId"])

    @mock.patch("tools.problem_seed.fetch_problem_index.request_json")
    def test_fetch_indexes_uses_both_sites(self, request_json: mock.Mock) -> None:
        request_json.side_effect = [
            mock.Mock(status=200, body=index_response([]), error=None),
            mock.Mock(status=200, body=index_response([]), error=None),
        ]

        raw = fetch_problem_index.fetch_indexes()

        self.assertEqual({"com", "cn"}, set(raw))
        self.assertEqual(2, request_json.call_count)

    def test_write_outputs_creates_jsonl_and_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output = Path(temp_dir)
            rows = [{"slug": "two-sum", "frontendId": 1, "difficulty": "EASY", "paidOnly": False, "onCom": True, "onCn": True}]
            manifest = {"unionCount": 1}

            fetch_problem_index.write_outputs(output, rows, manifest)

            self.assertEqual(rows[0], json.loads((output / "problem_index.jsonl").read_text(encoding="utf-8")))
            self.assertEqual(manifest, json.loads((output / "manifest.json").read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()
