import tempfile
import unittest
from pathlib import Path

from tools.learning_plan_template_seed import prepare_template_seed as seed
from tools.problem_seed.leetcode_api import read_jsonl


class PrepareTemplateSeedTest(unittest.TestCase):

    def test_build_seed_outputs_two_templates_and_required_metadata(self) -> None:
        source_rows = [
            row("Contains Duplicate", "0217-contains-duplicate", "Arrays & Hashing", "Easy", True, True),
            row("Two Sum", "0001-two-sum", "Arrays & Hashing", "Easy", True, True),
            row("Valid Parentheses", "0020-valid-parentheses", "Stack", "Easy", True, True),
            row("Missing Problem", "9999-missing-problem", "Graphs", "Medium", True, True),
        ]
        templates, refs, report = seed.build_seed(source_rows, {"contains-duplicate", "two-sum", "valid-parentheses"})

        self.assertEqual(2, len(templates))
        self.assertEqual({"neetcode_blind_75_interview_core", "neetcode_150_systematic_interview"},
                         {template["templateId"] for template in templates})
        self.assertEqual(8, len(refs))
        self.assertEqual(6, report["matchedProblemCount"])
        self.assertEqual(2, report["missingProblemCount"])
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

    def test_write_seed_creates_all_required_files(self) -> None:
        source_rows = [
            row("Contains Duplicate", "0217-contains-duplicate", "Arrays & Hashing", "Easy", True, True),
            row("Two Sum", "0001-two-sum", "Arrays & Hashing", "Easy", True, True),
        ]
        templates, refs, report = seed.build_seed(source_rows, {"contains-duplicate"})

        with tempfile.TemporaryDirectory() as temp_dir:
            output_dir = Path(temp_dir)
            seed.write_seed(output_dir, templates, refs, report)

            self.assertTrue((output_dir / seed.TEMPLATES_FILE).exists())
            self.assertTrue((output_dir / seed.PROBLEM_REFS_FILE).exists())
            self.assertTrue((output_dir / seed.MANIFEST_FILE).exists())
            self.assertTrue((output_dir / seed.METADATA_FILE).exists())
            self.assertEqual(2, len(read_jsonl(output_dir / seed.TEMPLATES_FILE)))
            metadata = (output_dir / seed.METADATA_FILE).read_text(encoding="utf-8")
            self.assertIn("学习计划模板 Seed 元数据", metadata)
            self.assertIn("草稿默认包含所有本地匹配题", metadata)
            self.assertNotIn("草稿每阶段最多推荐 5 道", metadata)

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
        source_rows = [
            row("Contains Duplicate", "0217-contains-duplicate", "Arrays & Hashing", "Easy", True, True),
        ]
        templates, refs, _ = seed.build_seed(source_rows, {"contains-duplicate"})
        refs[0]["phaseIndex"] = 99

        with self.assertRaisesRegex(ValueError, "unknown phase"):
            seed.validate_seed(templates, refs)


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
