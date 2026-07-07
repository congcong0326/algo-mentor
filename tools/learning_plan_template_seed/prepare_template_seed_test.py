import tempfile
import unittest
from pathlib import Path

from tools.learning_plan_template_seed import prepare_template_seed as seed
from tools.problem_seed.leetcode_api import read_jsonl


class PrepareTemplateSeedTest(unittest.TestCase):

    def test_build_seed_outputs_ten_templates_and_required_metadata(self) -> None:
        sources = source_data()
        index = build_problem_index(exclude={"missing-problem", "encode-and-decode-strings", "alien-dictionary"})

        templates, refs, report = seed.build_seed(sources, index)

        self.assertEqual(10, len(templates))
        self.assertEqual(set(seed.TEMPLATE_ORDER), {template["templateId"] for template in templates})
        self.assertEqual(10, report["templateCount"])
        self.assertIn("sources", report)
        self.assertGreaterEqual(len(report["sources"]), 5)
        self.assertGreater(report["matchedProblemCount"], 0)
        self.assertGreaterEqual(report["missingProblemCount"], 4)
        for template in templates:
            self.assertTrue(template["targetAudience"])
            self.assertTrue(template["difficultyMix"])
            self.assertTrue(template["prerequisites"])
            self.assertTrue(template["recommendedFor"])
            self.assertTrue(template["notRecommendedFor"])
            self.assertTrue(template["expectedOutcome"])
            self.assertTrue(template["sourceDescription"])
            self.assertTrue(template["curationNotes"])
            self.assertTrue(template["licenseNotice"])
            self.assertEqual(
                template["defaultDurationWeeks"],
                sum(phase["durationWeeks"] for phase in template["phases"]),
            )
        neetcode150 = next(
            template for template in templates if template["templateId"] == "neetcode_150_systematic_interview"
        )
        self.assertEqual(12, len(neetcode150["phases"]))
        graph_refs = [ref for ref in refs if ref["templateId"] == "topic_graph_bfs_dfs"]
        self.assertIn("alien-dictionary", [ref["problemSlug"] for ref in graph_refs])
        self.assertFalse(next(ref for ref in graph_refs if ref["problemSlug"] == "alien-dictionary")
                         ["metadata"]["matchedLocalProblem"])

    def test_write_seed_creates_all_required_files_and_stable_output(self) -> None:
        templates, refs, report = seed.build_seed(
            source_data(),
            build_problem_index(exclude={"missing-problem", "encode-and-decode-strings", "alien-dictionary"}),
        )

        with tempfile.TemporaryDirectory() as left, tempfile.TemporaryDirectory() as right:
            left_dir = Path(left)
            right_dir = Path(right)
            seed.write_seed(left_dir, templates, refs, report)
            seed.write_seed(right_dir, templates, refs, report)

            self.assertTrue((left_dir / seed.TEMPLATES_FILE).exists())
            self.assertTrue((left_dir / seed.PROBLEM_REFS_FILE).exists())
            self.assertTrue((left_dir / seed.MANIFEST_FILE).exists())
            self.assertTrue((left_dir / seed.METADATA_FILE).exists())
            self.assertEqual(10, len(read_jsonl(left_dir / seed.TEMPLATES_FILE)))
            metadata = (left_dir / seed.METADATA_FILE).read_text(encoding="utf-8")
            self.assertIn("学习计划模板 Seed 元数据", metadata)
            self.assertIn("草稿默认包含所有本地匹配题", metadata)
            self.assertNotIn("草稿每阶段最多推荐 5 道", metadata)
            manifest = (left_dir / seed.MANIFEST_FILE).read_text(encoding="utf-8")
            self.assertIn(seed.MANIFEST_FILE, manifest)
            for file_name in [seed.TEMPLATES_FILE, seed.PROBLEM_REFS_FILE, seed.MANIFEST_FILE, seed.METADATA_FILE]:
                self.assertEqual(
                    (left_dir / file_name).read_text(encoding="utf-8"),
                    (right_dir / file_name).read_text(encoding="utf-8"),
                )

    def test_parse_tih_best_practice_keeps_optional_and_premium_metadata(self) -> None:
        rows = seed.parse_tih_best_practice(tih_markdown())

        self.assertEqual(2, len(rows))
        self.assertFalse(rows[0]["optional"])
        self.assertFalse(rows[0]["premium"])
        self.assertTrue(rows[1]["optional"])
        self.assertTrue(rows[1]["premium"])
        self.assertEqual("encode-and-decode-strings", rows[1]["slug"])

    def test_validate_template_requires_target_audience(self) -> None:
        template = {
            "templateId": "x",
            "difficultyMix": {"Easy": 1},
            "prerequisites": ["basic"],
            "recommendedFor": ["interview"],
            "notRecommendedFor": ["none"],
            "expectedOutcome": "done",
            "sourceDescription": "source",
            "curationNotes": "notes",
            "licenseNotice": "license",
            "phases": [{"phaseIndex": 1}],
        }

        with self.assertRaisesRegex(ValueError, "targetAudience"):
            seed.validate_template(template)

    def test_validate_seed_rejects_problem_ref_with_unknown_phase(self) -> None:
        templates, refs, _ = seed.build_seed(
            source_data(),
            build_problem_index(exclude={"missing-problem", "encode-and-decode-strings", "alien-dictionary"}),
        )
        refs[0]["phaseIndex"] = 99

        with self.assertRaisesRegex(ValueError, "unknown phase"):
            seed.validate_seed(templates, refs)


def source_data() -> seed.SourceData:
    return seed.SourceData(
        neetcode_rows=[
            row("Two Sum", "0001-two-sum", "Arrays & Hashing", "Easy", True, True),
            row("Missing Problem", "9999-missing-problem", "Graphs", "Medium", True, True),
        ],
        tih_markdown=tih_markdown(),
        halfrost_meta={name: "" for name in seed.HALFROST_META_FILES},
    )


def tih_markdown() -> str:
    return """
## Week 1 - Sequences

| Question | Difficulty | LeetCode |
| :-- | --- | --- |
| Two Sum | Easy | [Link](https://leetcode.com/problems/two-sum/) |

#### Optional

| Question | Difficulty | LeetCode |
| :-- | --- | --- |
| Encode and Decode Strings | Medium | [Link](https://leetcode.com/problems/encode-and-decode-strings/) (Premium) |
"""


def build_problem_index(exclude: set[str] | None = None) -> seed.ProblemIndex:
    excluded = exclude or set()
    slugs = set()
    for template in seed.MANUAL_TEMPLATES.values():
        for phase in template["phases"]:
            slugs.update(phase["problemSlugs"])
    slugs.update({"two-sum"})
    return seed.ProblemIndex.from_slugs(sorted(slugs - excluded))


def row(
    problem: str,
    code: str,
    pattern: str,
    difficulty: str,
    blind75: bool,
    neetcode150: bool,
) -> dict:
    return {
        "problem": problem,
        "code": code,
        "pattern": pattern,
        "difficulty": difficulty,
        "link": f"{code[5:]}/",
        "blind75": blind75,
        "neetcode150": neetcode150,
    }


if __name__ == "__main__":
    unittest.main()
