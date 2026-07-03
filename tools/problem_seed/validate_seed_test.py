import json
import tempfile
import unittest
from pathlib import Path

from tools.problem_seed import validate_seed


def raw_question(slug: str = "two-sum", translated_content: str | None = "<p>ZH</p>") -> dict:
    return {
        "data": {
            "question": {
                "questionFrontendId": "1",
                "titleSlug": slug,
                "content": "<p>EN</p>",
                "translatedContent": translated_content,
                "topicTags": [
                    {"slug": "array", "name": "Array"},
                    {"slug": "hash-table", "name": "Hash Table"},
                ],
            }
        }
    }


class ValidateSeedTest(unittest.TestCase):

    def test_extract_slug_from_leetcode_url(self) -> None:
        self.assertEqual(
            "two-sum",
            validate_seed.extract_slug_from_leetcode_url("https://leetcode.com/problems/two-sum/?envType=study-plan"),
        )
        self.assertEqual(
            "cnHoX6",
            validate_seed.extract_slug_from_leetcode_url("https://leetcode.cn/problems/cnHoX6/"),
        )

    def test_merged_tag_arrays_aligns_cn_labels_by_slug(self) -> None:
        com_question = raw_question()["data"]["question"]
        cn_question = {
            "topicTags": [
                {"slug": "hash-table", "translatedName": "哈希表"},
                {"slug": "array", "translatedName": "数组"},
            ]
        }

        values, labels_en, labels_zh = validate_seed.merged_tag_arrays(com_question, cn_question)

        self.assertEqual(["array", "hash-table"], values)
        self.assertEqual(["Array", "Hash Table"], labels_en)
        self.assertEqual(["数组", "哈希表"], labels_zh)

    def test_build_validation_report_finds_duplicates_and_invalid_difficulty(self) -> None:
        rows = [
            {"slug": "two-sum", "frontendId": 1, "difficulty": "EASY", "paidOnly": False},
            {"slug": "two-sum", "frontendId": 1, "difficulty": "BAD", "paidOnly": False},
        ]

        report = validate_seed.build_validation_report(rows, Path("/tmp/not-used"))

        self.assertEqual(3, report["issueCount"])
        self.assertEqual(1, report["issuesByRule"]["duplicate_slug"])
        self.assertEqual(1, report["issuesByRule"]["duplicate_frontend_id"])
        self.assertEqual(1, report["issuesByRule"]["invalid_difficulty"])

    def test_build_validation_report_checks_complete_cached_problem(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            cache_dir = Path(temp_dir)
            cache_dir.joinpath("two-sum.com.json").write_text(json.dumps(raw_question("two-sum")), encoding="utf-8")
            cache_dir.joinpath("two-sum.cn.json").write_text(json.dumps(raw_question("two-sum")), encoding="utf-8")
            rows = [{"slug": "two-sum", "frontendId": 1, "difficulty": "EASY", "paidOnly": False}]

            report = validate_seed.build_validation_report(rows, cache_dir)

            self.assertEqual(1, report["checkedCount"])
            self.assertEqual(0, report["issueCount"])

    def test_validate_normalizable_problem_reports_title_slug_mismatch(self) -> None:
        issues = validate_seed.validate_normalizable_problem(
            "two-sum",
            raw_question("other-slug")["data"]["question"],
            raw_question("two-sum")["data"]["question"],
        )

        self.assertEqual("com_title_slug_mismatch", issues[0]["rule"])

    def test_build_seed_validation_report_accepts_cn_only_and_bilingual(self) -> None:
        rows = [
            {
                "slug": "two-sum",
                "frontendId": 1,
                "frontendDisplayId": "1",
                "titleEn": "Two Sum",
                "titleZh": "两数之和",
                "difficulty": "EASY",
                "tagValues": ["array"],
                "tagLabelsEn": ["Array"],
                "tagLabelsZh": ["数组"],
                "contentMarkdownEn": "# Two Sum",
                "contentMarkdownZh": "# 两数之和",
                "contentStatus": "BILINGUAL",
                "sourceSite": "LEETCODE_COM_CN",
                "leetcodeUrl": "https://leetcode.com/problems/two-sum/",
            },
            {
                "slug": "0H97ZC",
                "frontendId": None,
                "frontendDisplayId": "LCR 075",
                "titleEn": None,
                "titleZh": "数组的相对排序",
                "difficulty": "EASY",
                "tagValues": ["array"],
                "tagLabelsEn": ["Array"],
                "tagLabelsZh": ["数组"],
                "contentMarkdownEn": None,
                "contentMarkdownZh": "# 数组的相对排序",
                "contentStatus": "CN_ONLY",
                "sourceSite": "LEETCODE_CN",
                "leetcodeUrl": "https://leetcode.cn/problems/0H97ZC/",
            },
        ]

        report = validate_seed.build_seed_validation_report(rows, Path("problems.jsonl"))

        self.assertEqual(2, report["checkedCount"])
        self.assertEqual(0, report["issueCount"])

    def test_build_seed_validation_report_rejects_bad_final_seed_rows(self) -> None:
        rows = [
            {
                "slug": "two-sum",
                "frontendId": "1",
                "frontendDisplayId": "1",
                "titleEn": None,
                "titleZh": "两数之和",
                "difficulty": "BAD",
                "tagValues": ["array"],
                "tagLabelsEn": [],
                "tagLabelsZh": ["数组"],
                "contentMarkdownEn": None,
                "contentMarkdownZh": "# 两数之和",
                "contentStatus": "BILINGUAL",
                "sourceSite": "LEETCODE_COM_CN",
                "leetcodeUrl": "https://leetcode.com/problems/other/",
            },
        ]

        report = validate_seed.build_seed_validation_report(rows, Path("problems.jsonl"))

        self.assertGreater(report["issueCount"], 0)
        self.assertEqual(1, report["issuesByRule"]["invalid_frontend_id"])
        self.assertEqual(1, report["issuesByRule"]["invalid_difficulty"])
        self.assertEqual(1, report["issuesByRule"]["tag_array_length_mismatch"])
        self.assertEqual(1, report["issuesByRule"]["leetcode_url_slug_mismatch"])


if __name__ == "__main__":
    unittest.main()
