import json
import tempfile
import unittest
from pathlib import Path

from tools.problem_seed.prepare_seed import build_seed, write_seed


def response(question: dict) -> dict:
    return {"data": {"question": question}}


def question(**overrides: object) -> dict:
    raw = {
        "questionFrontendId": "1",
        "title": "Two Sum",
        "titleSlug": "two-sum",
        "content": "<p>English body</p>",
        "translatedTitle": "两数之和",
        "translatedContent": "<p>中文题面</p>",
        "difficulty": "Easy",
        "isPaidOnly": False,
        "topicTags": [
            {"slug": "array", "name": "Array", "translatedName": "数组"},
            {"slug": "hash-table", "name": "Hash Table", "translatedName": "哈希表"},
        ],
        "sampleTestCase": "[2,7,11,15]\n9",
        "codeSnippets": [{"langSlug": "python3", "code": "class Solution:\n    pass"}],
    }
    raw.update(overrides)
    return raw


class PrepareSeedTest(unittest.TestCase):

    def test_build_seed_outputs_bilingual_problem_from_api_cache(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            index = root / "problem_index.jsonl"
            cache = root / "cache"
            cache.mkdir()
            index.write_text(json.dumps({
                "slug": "two-sum",
                "frontendId": 1,
                "difficulty": "EASY",
                "paidOnly": False,
            }) + "\n", encoding="utf-8")
            (cache / "two-sum.com.json").write_text(json.dumps(response(question())), encoding="utf-8")
            (cache / "two-sum.cn.json").write_text(json.dumps(response(question())), encoding="utf-8")

            problems = build_seed(index, cache)

            self.assertEqual(1, len(problems))
            problem = problems[0]
            self.assertEqual("two-sum", problem.slug)
            self.assertEqual(1, problem.frontend_id)
            self.assertEqual("1", problem.frontend_display_id)
            self.assertEqual("BILINGUAL", problem.content_status)
            self.assertEqual("LEETCODE_COM_CN", problem.source_site)
            self.assertEqual("Two Sum", problem.title_en)
            self.assertEqual("两数之和", problem.title_zh)
            self.assertIn("# Two Sum", problem.content_markdown_en or "")
            self.assertIn("# 两数之和", problem.content_markdown_zh)
            self.assertEqual(["array", "hash-table"], problem.tag_values)
            self.assertEqual(["Array", "Hash Table"], problem.tag_labels_en)
            self.assertEqual(["数组", "哈希表"], problem.tag_labels_zh)
            self.assertEqual("class Solution:\n    pass", problem.python3_template)

    def test_build_seed_outputs_cn_only_with_display_id_and_null_english_fields(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            index = root / "problem_index.jsonl"
            cache = root / "cache"
            cache.mkdir()
            index.write_text(json.dumps({
                "slug": "0H97ZC",
                "frontendId": "LCR 075",
                "difficulty": "EASY",
                "paidOnly": False,
            }) + "\n", encoding="utf-8")
            (cache / "0H97ZC.cn.json").write_text(json.dumps(response(question(
                questionFrontendId="LCR 075",
                title="0H97ZC",
                titleSlug="0H97ZC",
                content=None,
                translatedTitle="数组的相对排序",
                translatedContent="<p>中文独有题面</p>",
            ))), encoding="utf-8")

            problems = build_seed(index, cache)

            self.assertEqual(1, len(problems))
            problem = problems[0]
            self.assertIsNone(problem.frontend_id)
            self.assertEqual("LCR 075", problem.frontend_display_id)
            self.assertEqual("CN_ONLY", problem.content_status)
            self.assertEqual("LEETCODE_CN", problem.source_site)
            self.assertIsNone(problem.title_en)
            self.assertIsNone(problem.content_markdown_en)
            self.assertEqual("数组的相对排序", problem.title_zh)
            self.assertIn("# 数组的相对排序", problem.content_markdown_zh)

    def test_build_seed_skips_premium_and_zh_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            index = root / "problem_index.jsonl"
            cache = root / "cache"
            cache.mkdir()
            index.write_text(
                json.dumps({"slug": "paid", "frontendId": 1, "difficulty": "HARD", "paidOnly": True}) + "\n"
                + json.dumps({"slug": "zh-missing", "frontendId": 2, "difficulty": "EASY", "paidOnly": False}) + "\n",
                encoding="utf-8",
            )
            (cache / "paid.com.json").write_text(json.dumps(response(question(titleSlug="paid"))), encoding="utf-8")
            (cache / "paid.cn.json").write_text(json.dumps(response(question(titleSlug="paid"))), encoding="utf-8")
            (cache / "zh-missing.com.json").write_text(json.dumps(response(question(titleSlug="zh-missing"))), encoding="utf-8")

            problems = build_seed(index, cache)

            self.assertEqual([], problems)

    def test_write_seed_outputs_manifest_and_empty_categories(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            index = root / "problem_index.jsonl"
            cache = root / "cache"
            output = root / "seed"
            cache.mkdir()
            index.write_text(json.dumps({"slug": "two-sum", "frontendId": 1, "difficulty": "EASY"}) + "\n", encoding="utf-8")
            (cache / "two-sum.com.json").write_text(json.dumps(response(question())), encoding="utf-8")
            (cache / "two-sum.cn.json").write_text(json.dumps(response(question())), encoding="utf-8")

            write_seed(output, build_seed(index, cache), "leetcode-api@abc123")

            manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual("leetcode-api@abc123", manifest["sourceCommit"])
            self.assertEqual(1, manifest["problemCount"])
            self.assertEqual({"BILINGUAL": 1}, manifest["contentStatusCounts"])
            self.assertEqual("", (output / "problem_categories.jsonl").read_text(encoding="utf-8"))
            self.assertEqual("", (output / "problem_category_items.jsonl").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
