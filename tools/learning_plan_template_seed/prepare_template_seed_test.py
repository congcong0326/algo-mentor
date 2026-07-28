import tempfile
import unittest
from pathlib import Path

from tools.learning_plan_template_seed import prepare_template_seed as seed
from tools.problem_seed.leetcode_api import read_jsonl


class PrepareTemplateSeedTest(unittest.TestCase):

    def test_build_seed_outputs_configured_templates_and_required_metadata(self) -> None:
        sources = source_data()
        index = build_problem_index(exclude=known_missing_slugs())
        expected_template_ids = seed.load_template_order(seed.DEFAULT_TEMPLATE_SOURCE_DIR, seed.DEFAULT_TEMPLATE_ORDER_PATH)

        templates, refs, report = seed.build_seed(sources, index)

        self.assertEqual(len(expected_template_ids), len(templates))
        self.assertEqual(set(expected_template_ids), {template["templateId"] for template in templates})
        self.assertEqual(len(expected_template_ids), report["templateCount"])
        self.assertIn("sources", report)
        self.assertGreaterEqual(len(report["sources"]), 5)
        self.assertGreater(report["matchedProblemCount"], 0)
        self.assertGreaterEqual(report["missingProblemCount"], 4)
        self.assertEqual(5, sum(template["catalogCategory"] == "SYSTEMATIC_LEARNING" for template in templates))
        self.assertEqual(8, sum(template["catalogCategory"] == "INTERVIEW_PREP" for template in templates))
        self.assertEqual(18, sum(template["catalogCategory"] == "TOPIC_BREAKTHROUGH" for template in templates))
        self.assertEqual(4, sum(template["catalogCategory"] == "LANGUAGE_AND_ROLE" for template in templates))
        self.assertEqual(
            {
                "leetcode_75_core_sprint": 1,
                "cn_algorithm_foundation_12weeks": 2,
                "carl_algorithm_roadmap_full": 3,
                "leetcode_top_interview_150": 4,
                "labuladong_algo_thinking": 5,
                "topic_dynamic_programming_foundation": 6,
            },
            {
                template["templateId"]: template["recommendedOrder"]
                for template in templates
                if template.get("recommendedOrder") is not None
            },
        )
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

    def test_template_source_directory_contains_ordered_template_files(self) -> None:
        expected_template_ids = seed.load_template_order(seed.DEFAULT_TEMPLATE_SOURCE_DIR, seed.DEFAULT_TEMPLATE_ORDER_PATH)

        self.assertEqual(35, len(expected_template_ids))
        for template_id in expected_template_ids:
            template_dir = seed.DEFAULT_TEMPLATE_SOURCE_DIR / template_id
            self.assertTrue((template_dir / seed.TEMPLATE_SOURCE_TEMPLATE_FILE).exists(), template_id)
            self.assertTrue((template_dir / seed.TEMPLATE_SOURCE_PROBLEM_REFS_FILE).exists(), template_id)

    def test_p1_a_batch_one_templates_meet_topic_breakthrough_thresholds(self) -> None:
        templates, refs, _ = seed.build_seed(
            source_data(),
            build_problem_index(exclude=known_missing_slugs()),
        )
        templates_by_id = {template["templateId"]: template for template in templates}

        self.assertEqual(6, len(seed.P1_A_BATCH_ONE_TEMPLATE_IDS))
        for template_id in seed.P1_A_BATCH_ONE_TEMPLATE_IDS:
            template = templates_by_id[template_id]
            template_refs = [ref for ref in refs if ref["templateId"] == template_id]

            self.assertEqual("TOPIC_BREAKTHROUGH", template["intent"])
            self.assertEqual("INTERMEDIATE", template["level"])
            self.assertGreaterEqual(len(template_refs), 15)
            self.assertEqual(
                template["defaultDurationWeeks"],
                sum(phase["durationWeeks"] for phase in template["phases"]),
            )
            if template["defaultDurationWeeks"] == 2:
                self.assertEqual(2, len(template["phases"]))
            if template["defaultDurationWeeks"] == 3:
                self.assertEqual(3, len(template["phases"]))
            for phase in template["phases"]:
                matched_refs = [
                    ref for ref in template_refs
                    if ref["phaseIndex"] == phase["phaseIndex"] and ref["metadata"]["matchedLocalProblem"]
                ]
                self.assertGreaterEqual(len(matched_refs), 3, f"{template_id} phase {phase['phaseIndex']}")

    def test_p1_a_batch_two_templates_meet_topic_breakthrough_thresholds(self) -> None:
        templates, refs, _ = seed.build_seed(
            source_data(),
            build_problem_index(exclude=known_missing_slugs()),
        )
        templates_by_id = {template["templateId"]: template for template in templates}

        self.assertEqual(6, len(seed.P1_A_BATCH_TWO_TEMPLATE_IDS))
        batch_refs = []
        for template_id in seed.P1_A_BATCH_TWO_TEMPLATE_IDS:
            template = templates_by_id[template_id]
            template_refs = [ref for ref in refs if ref["templateId"] == template_id]
            batch_refs.extend(template_refs)

            self.assertEqual("TOPIC_BREAKTHROUGH", template["intent"])
            expected_level = "BEGINNER" if template_id == "topic_linked_list" else "INTERMEDIATE"
            self.assertEqual(expected_level, template["level"])
            self.assertGreaterEqual(len(template_refs), 15)
            self.assertEqual(
                template["defaultDurationWeeks"],
                sum(phase["durationWeeks"] for phase in template["phases"]),
            )
            if template["defaultDurationWeeks"] == 2:
                self.assertEqual(2, len(template["phases"]))
            if template["defaultDurationWeeks"] == 3:
                self.assertEqual(3, len(template["phases"]))
            for phase in template["phases"]:
                matched_refs = [
                    ref for ref in template_refs
                    if ref["phaseIndex"] == phase["phaseIndex"] and ref["metadata"]["matchedLocalProblem"]
                ]
                self.assertGreaterEqual(len(matched_refs), 3, f"{template_id} phase {phase['phaseIndex']}")

        self.assertEqual(111, len(batch_refs))
        self.assertTrue(all(ref["metadata"]["matchedLocalProblem"] for ref in batch_refs))

    def test_selected_p1_b_batch_has_complete_routes(self) -> None:
        templates, refs, _ = seed.build_seed(
            source_data(),
            build_problem_index(exclude=known_missing_slugs()),
        )
        templates_by_id = {template["templateId"]: template for template in templates}

        self.assertEqual(4, len(seed.P1_B_SELECTED_BATCH_TEMPLATE_IDS))
        expected = {
            "tih_algorithm_essentials": (6, 119),
            "topic_dp_advanced": (4, 31),
            "sword_offer_classic": (8, 75),
            "leetcode_patterns_beginner_roadmap": (10, 68),
        }
        for template_id, (phase_count, problem_count) in expected.items():
            template = templates_by_id[template_id]
            template_refs = [ref for ref in refs if ref["templateId"] == template_id]

            self.assertEqual(phase_count, len(template["phases"]))
            self.assertEqual(problem_count, len(template_refs))
            self.assertEqual(
                template["defaultDurationWeeks"],
                sum(phase["durationWeeks"] for phase in template["phases"]),
            )
            self.assertEqual(problem_count, len({ref["problemSlug"] for ref in template_refs}))

        dp_refs = [ref for ref in refs if ref["templateId"] == "topic_dp_advanced"]
        self.assertTrue(all(ref["metadata"]["matchedLocalProblem"] for ref in dp_refs))

    def test_p1_b_interview_routes_are_complete_and_fully_matched(self) -> None:
        templates, refs, _ = seed.build_seed(
            source_data(),
            build_problem_index(exclude=known_missing_slugs()),
        )
        templates_by_id = {template["templateId"]: template for template in templates}
        expected = {
            "cracking_coding_interview_classic": (10, 109),
            "leetcode_75_core_sprint": (6, 75),
            "leetcode_top_interview_150": (10, 150),
        }

        self.assertEqual(list(expected), seed.P1_B_INTERVIEW_ROUTE_TEMPLATE_IDS)
        for template_id, (phase_count, problem_count) in expected.items():
            template = templates_by_id[template_id]
            template_refs = [ref for ref in refs if ref["templateId"] == template_id]

            self.assertEqual(phase_count, len(template["phases"]))
            self.assertEqual(problem_count, len(template_refs))
            self.assertEqual(problem_count, len({ref["problemSlug"] for ref in template_refs}))
            self.assertTrue(all(ref["metadata"]["matchedLocalProblem"] for ref in template_refs))
            self.assertEqual(
                template["defaultDurationWeeks"],
                sum(phase["durationWeeks"] for phase in template["phases"]),
            )

    def test_final_compatible_templates_are_complete_and_auditable(self) -> None:
        templates, refs, _ = seed.build_seed(
            source_data(),
            build_problem_index(exclude=known_missing_slugs()),
        )
        templates_by_id = {template["templateId"]: template for template in templates}
        expected = {
            "carl_algorithm_roadmap_full": (11, 144, 0, "Java"),
            "labuladong_algo_thinking": (8, 64, 0, "Java"),
            "leetcode_sql_50": (7, 50, 0, "SQL"),
            "leetcode_javascript_30_days": (5, 30, 0, "JavaScript"),
            "leetcode_pandas_introduction": (4, 15, 0, "Python3"),
            "leetcode_pandas_30_days": (5, 33, 5, "Python3"),
        }

        self.assertEqual(
            ["carl_algorithm_roadmap_full", "labuladong_algo_thinking"],
            seed.P1_B_FINAL_ROADMAP_TEMPLATE_IDS,
        )
        self.assertEqual(
            [
                "leetcode_sql_50",
                "leetcode_javascript_30_days",
                "leetcode_pandas_introduction",
                "leetcode_pandas_30_days",
            ],
            seed.P2_COMPATIBLE_TEMPLATE_IDS,
        )
        for template_id, (phase_count, problem_count, missing_count, language) in expected.items():
            template = templates_by_id[template_id]
            template_refs = [ref for ref in refs if ref["templateId"] == template_id]

            self.assertEqual(phase_count, len(template["phases"]))
            self.assertEqual(problem_count, len(template_refs))
            self.assertEqual(language, template["programmingLanguage"])
            self.assertEqual(
                missing_count,
                sum(not ref["metadata"]["matchedLocalProblem"] for ref in template_refs),
            )
            self.assertEqual(problem_count, len({ref["problemSlug"] for ref in template_refs}))
            self.assertEqual(
                template["defaultDurationWeeks"],
                sum(phase["durationWeeks"] for phase in template["phases"]),
            )

    def test_write_seed_creates_all_required_files_and_stable_output(self) -> None:
        templates, refs, report = seed.build_seed(
            source_data(),
            build_problem_index(exclude=known_missing_slugs()),
        )
        expected_template_ids = seed.load_template_order(seed.DEFAULT_TEMPLATE_SOURCE_DIR, seed.DEFAULT_TEMPLATE_ORDER_PATH)

        with tempfile.TemporaryDirectory() as left, tempfile.TemporaryDirectory() as right:
            left_dir = Path(left)
            right_dir = Path(right)
            seed.write_seed(left_dir, templates, refs, report)
            seed.write_seed(right_dir, templates, refs, report)

            self.assertTrue((left_dir / seed.TEMPLATES_FILE).exists())
            self.assertTrue((left_dir / seed.PROBLEM_REFS_FILE).exists())
            self.assertTrue((left_dir / seed.MANIFEST_FILE).exists())
            self.assertTrue((left_dir / seed.METADATA_FILE).exists())
            self.assertEqual(len(expected_template_ids), len(read_jsonl(left_dir / seed.TEMPLATES_FILE)))
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

    def test_validate_seed_rejects_invalid_catalog_category_and_recommendation_order(self) -> None:
        templates, refs, _ = seed.build_seed(
            source_data(),
            build_problem_index(exclude=known_missing_slugs()),
        )
        templates[0]["catalogCategory"] = "UNSUPPORTED"

        with self.assertRaisesRegex(ValueError, "catalogCategory"):
            seed.validate_seed(templates, refs)

        templates[0]["catalogCategory"] = "INTERVIEW_PREP"
        templates[0]["recommendedOrder"] = 0

        with self.assertRaisesRegex(ValueError, "recommendedOrder"):
            seed.validate_seed(templates, refs)

    def test_validate_seed_rejects_problem_ref_with_unknown_phase(self) -> None:
        templates, refs, _ = seed.build_seed(
            source_data(),
            build_problem_index(exclude=known_missing_slugs()),
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
    for refs_path in seed.DEFAULT_TEMPLATE_SOURCE_DIR.glob(f"*/{seed.TEMPLATE_SOURCE_PROBLEM_REFS_FILE}"):
        for ref in read_jsonl(refs_path):
            slugs.add(ref["problemSlug"])
    return seed.ProblemIndex.from_slugs(sorted(slugs - excluded))


def known_missing_slugs() -> set[str]:
    return {
        "alien-dictionary",
        "encode-and-decode-strings",
        "graph-valid-tree",
        "meeting-rooms",
        "meeting-rooms-ii",
        "number-of-connected-components-in-an-undirected-graph",
        "minimum-knight-moves",
        "design-hit-counter",
        "strobogrammatic-number-ii",
        "walls-and-gates",
        "count-occurrences-in-text",
        "the-number-of-rich-customers",
        "immediate-food-delivery-i",
        "ads-performance",
        "accepted-candidates-from-the-interviews",
    }


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
