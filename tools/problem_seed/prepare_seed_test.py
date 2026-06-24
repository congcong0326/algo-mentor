import json
import tempfile
import unittest
from pathlib import Path

from tools.problem_seed.prepare_seed import build_seed, write_seed


class PrepareSeedTest(unittest.TestCase):

    def test_build_seed_strips_solution_and_reads_python_template(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            source = Path(temp_dir) / "source"
            (source / "problemset_md").mkdir(parents=True)
            (source / "problemset").mkdir()
            (source / "problemset_md" / "1.md").write_text(
                "# Two Sum\n\nBody\n\n## solution 题解\n\nhidden",
                encoding="utf-8",
            )
            (source / "problemset" / "1.json").write_text(json.dumps({
                "questionFrontendId": "1",
                "title": "Two Sum",
                "translatedTitle": "两数之和",
                "titleSlug": "two-sum",
                "difficulty": "Easy",
                "content": "<p>Body</p>",
                "translatedContent": "<p>中文题面</p>",
                "topicTags": [
                    {"slug": "array", "name": "Array", "translatedName": "数组"},
                    {"slug": "hash-table", "name": "Hash Table", "translatedName": "哈希表"},
                ],
                "sampleTestCase": "[2,7,11,15]\n9",
                "codeSnippets": [{"langSlug": "python3", "code": "class Solution:\n    pass"}],
            }), encoding="utf-8")

            problems = build_seed(source)

            self.assertEqual(1, len(problems))
            self.assertEqual("two-sum", problems[0].slug)
            self.assertEqual(1, problems[0].frontend_id)
            self.assertEqual("EASY", problems[0].difficulty)
            self.assertEqual("Two Sum", problems[0].title_en)
            self.assertEqual("两数之和", problems[0].title_zh)
            self.assertEqual(["array", "hash-table"], problems[0].tag_values)
            self.assertEqual(["Array", "Hash Table"], problems[0].tag_labels_en)
            self.assertEqual(["数组", "哈希表"], problems[0].tag_labels_zh)
            self.assertIn("# Two Sum", problems[0].content_markdown_en)
            self.assertIn("# 两数之和", problems[0].content_markdown_zh)
            self.assertNotIn("hidden", problems[0].content_markdown_en)
            self.assertEqual("class Solution:\n    pass", problems[0].python3_template)

    def test_build_seed_skips_bad_json_and_allows_empty_code_snippets(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            source = Path(temp_dir) / "source"
            (source / "problemset_md").mkdir(parents=True)
            (source / "problemset").mkdir()
            (source / "problemset_md" / "valid-problem.md").write_text("# Valid Problem\n\nBody", encoding="utf-8")
            (source / "problemset" / "valid-problem.json").write_text("{not json", encoding="utf-8")

            problems = build_seed(source)

            self.assertEqual(1, len(problems))
            self.assertEqual("valid-problem", problems[0].slug)
            self.assertEqual("Valid Problem", problems[0].title_zh)
            self.assertIsNone(problems[0].python3_template)

    def test_write_seed_outputs_manifest_and_empty_categories(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            source = Path(temp_dir) / "source"
            (source / "problemset_md").mkdir(parents=True)
            (source / "problemset_md" / "two-sum.md").write_text("# Two Sum\n\nBody", encoding="utf-8")
            problems = build_seed(source)
            output = Path(temp_dir) / "seed"

            write_seed(output, problems, "abc123")

            manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual("abc123", manifest["sourceCommit"])
            self.assertEqual(1, manifest["problemCount"])
            self.assertEqual("", (output / "problem_categories.jsonl").read_text(encoding="utf-8"))
            self.assertEqual("", (output / "problem_category_items.jsonl").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
