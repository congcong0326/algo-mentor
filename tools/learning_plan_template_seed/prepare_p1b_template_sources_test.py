import unittest

from tools.learning_plan_template_seed import prepare_p1b_template_sources as batch


class PrepareP1bTemplateSourcesTest(unittest.TestCase):

    def test_parse_tih_questions_keeps_sections_and_deduplicates_slugs(self) -> None:
        markdowns = {theme: "" for theme in batch.TIH_THEME_FILES}
        markdowns["array"] = """
## Essential questions

- [Two Sum](https://leetcode.com/problems/two-sum/)

## Recommended practice questions

- [Minimum Window Substring](https://leetcode.com/problems/minimum-window-substring/description/)
"""
        markdowns["string"] = """
## Essential questions

- [Minimum Window Substring](https://leetcode.com/problems/minimum-window-substring/)
"""

        questions, slugs_by_theme = batch.parse_tih_algorithm_questions(markdowns)

        self.assertEqual({"two-sum", "minimum-window-substring"}, set(questions))
        self.assertEqual(["array", "string"], questions["minimum-window-substring"]["themes"])
        self.assertEqual(["recommended", "essential"], questions["minimum-window-substring"]["sections"])
        self.assertEqual(["two-sum", "minimum-window-substring"], slugs_by_theme["array"])

    def test_parse_sean_beginner_phases_preserves_source_order(self) -> None:
        text = """
export const beginnerRoadmap = {
  phases: [
    { title: "Phase 1: Arrays & Hash Tables", questions: [
      { slug: "two-sum", note: "ignored" },
      { slug: "contains-duplicate", note: "ignored" },
    ] },
    { title: "Phase 2: Two Pointers", questions: [
      { slug: "move-zeroes", note: "ignored" },
    ] },
  ],
};
export const experiencedRoadmap = {};
"""

        phases = batch.parse_sean_beginner_phases(text)

        self.assertEqual(["Arrays & Hash Tables", "Two Pointers"], [phase["name"] for phase in phases])
        self.assertEqual(["two-sum", "contains-duplicate"], phases[0]["slugs"])
        self.assertEqual(["move-zeroes"], phases[1]["slugs"])

    def test_sword_offer_order_handles_numbered_variants(self) -> None:
        rows = [
            {"frontend_id": "面试题10- II"},
            {"frontend_id": "面试题09"},
            {"frontend_id": "面试题10- I"},
        ]

        ordered = sorted(rows, key=batch.sword_offer_order)

        self.assertEqual(
            ["面试题09", "面试题10- I", "面试题10- II"],
            [row["frontend_id"] for row in ordered],
        )

    def test_lcci_template_resolves_source_slug_by_frontend_display_id(self) -> None:
        index = batch.ProblemIndex.from_rows([{
            "slug": "binary-number-to-string-lcci",
            "frontendDisplayId": "面试题 05.02",
            "titleZh": "二进制数转字符串",
            "difficulty": "MEDIUM",
            "tagValues": ["string"],
        }])

        _, refs = batch.build_lcci_template(index, [{
            "frontend_id": "面试题 05.02",
            "title": "Bianry Number to String",
            "title_cn": "二进制数转字符串",
            "url_cn": "https://leetcode.cn/problems/bianry-number-to-string-lcci",
            "difficulty": "Medium",
            "tags": ["String"],
        }])

        self.assertEqual("binary-number-to-string-lcci", refs[0]["problemSlug"])
        self.assertEqual("frontendDisplayId", refs[0]["metadata"]["matchedBy"])
        self.assertEqual(4, refs[0]["phaseIndex"])

    def test_official_study_plan_builder_preserves_group_question_order(self) -> None:
        detail = {
            "name": "Example Plan",
            "planSubGroups": [{
                "name": "Array",
                "questions": [
                    {"title": "Two Sum", "titleSlug": "two-sum", "difficulty": "EASY", "paidOnly": False},
                    {"title": "Three Sum", "titleSlug": "3sum", "difficulty": "MEDIUM", "paidOnly": False},
                ],
            }],
        }
        index = batch.ProblemIndex.from_rows([
            {"slug": "two-sum", "frontendDisplayId": "1", "difficulty": "EASY"},
            {"slug": "3sum", "frontendDisplayId": "15", "difficulty": "MEDIUM"},
        ])

        template, refs = batch.build_official_study_plan_template(
            problem_index=index,
            detail=detail,
            template_id="example",
            plan_slug="example",
            title="示例计划",
            summary="summary",
            goal="goal",
            duration_weeks=1,
            weekly_hours=5,
            target_audience="audience",
            prerequisites=["basic"],
            recommended_for=["practice"],
            not_recommended_for=["none"],
            expected_outcome="done",
            phases=[batch.official_phase("数组", "focus", ["Array"], ["Array"])],
        )

        self.assertEqual(1, len(template["phases"]))
        self.assertEqual(["two-sum", "3sum"], [ref["problemSlug"] for ref in refs])
        self.assertEqual([1, 2], [ref["sourceOrder"] for ref in refs])

    def test_official_study_plan_builder_preserves_duplicate_group_occurrences(self) -> None:
        detail = {
            "name": "Repeated Groups",
            "planSubGroups": [
                {"name": "Review", "questions": [
                    {"title": "Two Sum", "titleSlug": "two-sum", "difficulty": "EASY"},
                ]},
                {"name": "Review", "questions": [
                    {"title": "Three Sum", "titleSlug": "3sum", "difficulty": "MEDIUM"},
                ]},
            ],
        }
        index = batch.ProblemIndex.from_rows([
            {"slug": "two-sum", "frontendDisplayId": "1", "difficulty": "EASY"},
            {"slug": "3sum", "frontendDisplayId": "15", "difficulty": "MEDIUM"},
        ])

        template, refs = batch.build_official_study_plan_template(
            problem_index=index,
            detail=detail,
            template_id="example",
            plan_slug="example",
            title="示例计划",
            summary="summary",
            goal="goal",
            duration_weeks=2,
            weekly_hours=5,
            target_audience="audience",
            prerequisites=["basic"],
            recommended_for=["practice"],
            not_recommended_for=["none"],
            expected_outcome="done",
            phases=[
                batch.official_phase("复盘一", "focus", ["Array"], ["Review"]),
                batch.official_phase("复盘二", "focus", ["Array"], ["Review"]),
            ],
            programming_language="SQL",
        )

        self.assertEqual("SQL", template["programmingLanguage"])
        self.assertEqual(["two-sum", "3sum"], [ref["problemSlug"] for ref in refs])
        self.assertEqual([1, 2], [ref["metadata"]["sourceGroupOccurrence"] for ref in refs])

    def test_carl_parser_maps_numeric_and_interview_problem_ids(self) -> None:
        blocks = []
        for index, section in enumerate(batch.CARL_SECTION_ORDER):
            path = "./problems/0001.两数之和.md"
            if index == 1:
                path = "./problems/面试题02.07.链表相交.md"
            blocks.append(f"<details><summary><b>{section}</b></summary>[题目]({path})</details>")
        index = batch.ProblemIndex.from_rows([
            {"slug": "two-sum", "frontendDisplayId": "1", "difficulty": "EASY"},
            {"slug": "intersection-of-two-linked-lists-lcci", "frontendDisplayId": "面试题 02.07", "difficulty": "EASY"},
        ])

        sections = batch.parse_carl_problem_sections("\n".join(blocks), index)

        self.assertEqual("two-sum", sections["数组"][0]["slug"])
        self.assertEqual("intersection-of-two-linked-lists-lcci", sections["链表"][0]["slug"])

    def test_labuladong_route_contains_unique_locally_matched_problems(self) -> None:
        slugs = [slug for phase in batch.LABULADONG_PHASE_SLUGS for slug in phase]
        index = batch.ProblemIndex.from_rows([
            {"slug": slug, "frontendDisplayId": str(position), "difficulty": "MEDIUM"}
            for position, slug in enumerate(slugs, start=1)
        ])

        template, refs = batch.build_labuladong_algo_thinking_template(index)

        self.assertEqual(8, len(template["phases"]))
        self.assertEqual(64, len(refs))
        self.assertEqual(64, len({ref["problemSlug"] for ref in refs}))

    def test_lcci_phase_split_keeps_hard_chapter_boundaries(self) -> None:
        self.assertEqual(8, batch.lcci_phase_index(17, 13))
        self.assertEqual(9, batch.lcci_phase_index(17, 14))
        self.assertEqual(10, batch.lcci_phase_index(17, 20))


if __name__ == "__main__":
    unittest.main()
