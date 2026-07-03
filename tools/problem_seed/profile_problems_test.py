import json
import tempfile
import unittest
from pathlib import Path

from tools.problem_seed import profile_problems


def raw_question(
    *,
    frontend_id: str = "1",
    content: str | None = "<p>EN</p>",
    translated_content: str | None = "<p>ZH</p>",
    tags: list[dict] | None = None,
) -> dict:
    return {
        "data": {
            "question": {
                "questionFrontendId": frontend_id,
                "titleSlug": "two-sum",
                "content": content,
                "translatedContent": translated_content,
                "topicTags": tags or [{"slug": "array", "name": "Array", "translatedName": "数组"}],
            }
        }
    }


class ProfileProblemsTest(unittest.TestCase):

    def test_classify_problem_covers_five_categories(self) -> None:
        row = {"paidOnly": False}

        self.assertEqual(
            "complete",
            profile_problems.classify_problem(row, raw_question(content="en"), raw_question(translated_content="zh")),
        )
        self.assertEqual(
            "zh_missing",
            profile_problems.classify_problem(row, raw_question(content="en"), raw_question(translated_content=None)),
        )
        self.assertEqual(
            "cn_only",
            profile_problems.classify_problem(row, raw_question(content=None), raw_question(translated_content="zh")),
        )
        self.assertEqual(
            "premium_locked",
            profile_problems.classify_problem({"paidOnly": True}, raw_question(content=None), raw_question(translated_content=None)),
        )
        self.assertEqual("deprecated", profile_problems.classify_problem(row, None, None))

    def test_frontend_id_mismatch_reports_different_ids(self) -> None:
        mismatch = profile_problems.frontend_id_mismatch(
            "two-sum",
            raw_question(frontend_id="1")["data"]["question"],
            raw_question(frontend_id="2")["data"]["question"],
        )

        self.assertEqual({"slug": "two-sum", "comFrontendId": "1", "cnFrontendId": "2"}, mismatch)

    def test_build_profile_report_counts_and_examples(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            cache_dir = Path(temp_dir)
            (cache_dir / "two-sum.com.json").write_text(json.dumps(raw_question(frontend_id="1")), encoding="utf-8")
            (cache_dir / "two-sum.cn.json").write_text(json.dumps(raw_question(frontend_id="2")), encoding="utf-8")
            rows = [{
                "slug": "two-sum",
                "frontendId": 1,
                "difficulty": "EASY",
                "paidOnly": False,
                "onCom": True,
                "onCn": True,
            }]

            report = profile_problems.build_profile_report(rows, cache_dir)

            self.assertEqual(1, report["counts"]["complete"])
            self.assertEqual(1, report["freeCounts"]["complete"])
            self.assertEqual(["two-sum"], report["examples"]["complete"])
            self.assertEqual({"EASY": 1}, report["difficultyDistribution"]["complete"])
            self.assertEqual(1, report["frontendIdMismatchCount"])


if __name__ == "__main__":
    unittest.main()

