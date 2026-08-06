#!/usr/bin/env python3
"""生成本批学习计划模板的可维护源文件。"""

from __future__ import annotations

import argparse
import json
import re
import urllib.request
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    import sys

    sys.path.append(str(Path(__file__).resolve().parents[2]))

from tools.problem_seed.leetcode_api import read_jsonl, write_json, write_jsonl


DEFAULT_OUTPUT_ROOT = Path("data/learning-plan-template-sources/templates")
DEFAULT_LOCAL_PROBLEMS_PATH = Path("data/seed/problems.jsonl")

TIH_COMMIT = "8ee2acb54a05c4add123a824d15e7dfc4e703b2f"
TIH_SOURCE_NAME = "yangshun/tech-interview-handbook"
TIH_SOURCE_URL = "https://github.com/yangshun/tech-interview-handbook"
TIH_RAW_BASE = (
    "https://raw.githubusercontent.com/yangshun/tech-interview-handbook/"
    f"{TIH_COMMIT}/apps/website/contents/algorithms"
)

SEAN_COMMIT = "514b971570bcc8d6cd9a354d561245e8ef52a603"
SEAN_SOURCE_NAME = "seanprashad/leetcode-patterns"
SEAN_SOURCE_URL = "https://github.com/seanprashad/leetcode-patterns"
SEAN_RAW_BASE = (
    "https://raw.githubusercontent.com/seanprashad/leetcode-patterns/"
    f"{SEAN_COMMIT}/src/data"
)

DOOCS_COMMIT = "c0a8f9df1b2e6e2da564acda398d345cb3dd0710"
DOOCS_SOURCE_NAME = "doocs/leetcode"
DOOCS_SOURCE_URL = "https://github.com/doocs/leetcode"
DOOCS_LCOF_URL = (
    "https://raw.githubusercontent.com/doocs/leetcode/"
    f"{DOOCS_COMMIT}/lcof/lcof.json"
)
DOOCS_LCCI_URL = (
    "https://raw.githubusercontent.com/doocs/leetcode/"
    f"{DOOCS_COMMIT}/lcci/lcci.json"
)

LEETCODE_MASTER_COMMIT = "86f78fde8cb62d10c3b5e38b7e6b6e0705850f92"
LEETCODE_MASTER_SOURCE_NAME = "youngyangyang04/leetcode-master"
LEETCODE_MASTER_SOURCE_URL = "https://github.com/youngyangyang04/leetcode-master"
LEETCODE_MASTER_README_URL = (
    "https://raw.githubusercontent.com/youngyangyang04/leetcode-master/"
    f"{LEETCODE_MASTER_COMMIT}/README.md"
)

LABULADONG_COMMIT = "b1f23cb9605f6146ff78bafad71e795176439b99"
LABULADONG_SOURCE_NAME = "labuladong/fucking-algorithm"
LABULADONG_SOURCE_URL = "https://github.com/labuladong/fucking-algorithm"

LEETCODE_SOURCE_NAME = "LeetCode official Study Plan"
LEETCODE_SOURCE_URL = "https://leetcode.com/studyplan/"
LEETCODE_SOURCE_VERSION = "accessed-2026-07-28"
LEETCODE_GRAPHQL_URL = "https://leetcode.com/graphql/"
LEETCODE_STUDY_PLAN_QUERY = """
query studyPlanV2Detail($slug: String!) {
  studyPlanV2Detail(planSlug: $slug) {
    name
    planSubGroups {
      name
      questions {
        title
        titleSlug
        difficulty
        paidOnly
      }
    }
  }
}
"""

LOCAL_SOURCE_NAME = "algo-mentor local problem seed"
LOCAL_SOURCE_COMMIT = "local-2026-07-28"

TEMPLATE_IDS = [
    "tih_algorithm_essentials",
    "topic_dp_advanced",
    "sword_offer_classic",
    "leetcode_patterns_beginner_roadmap",
    "cracking_coding_interview_classic",
    "leetcode_75_core_sprint",
    "leetcode_top_interview_150",
    "carl_algorithm_roadmap_full",
    "labuladong_algo_thinking",
    "leetcode_sql_50",
    "leetcode_javascript_30_days",
    "leetcode_pandas_introduction",
    "leetcode_pandas_30_days",
]

CARL_SECTION_ORDER = [
    "数组",
    "链表",
    "哈希表",
    "字符串",
    "双指针法",
    "栈与队列",
    "二叉树",
    "回溯算法",
    "贪心算法",
    "动态规划",
    "单调栈",
    "图论",
]

LABULADONG_PHASE_SLUGS = [
    [
        "two-sum",
        "remove-duplicates-from-sorted-array",
        "move-zeroes",
        "reverse-string",
        "valid-palindrome",
        "linked-list-cycle",
        "middle-of-the-linked-list",
        "squares-of-a-sorted-array",
    ],
    [
        "minimum-size-subarray-sum",
        "longest-substring-without-repeating-characters",
        "find-first-and-last-position-of-element-in-sorted-array",
        "search-in-rotated-sorted-array",
        "subarray-sum-equals-k",
        "range-sum-query-immutable",
        "corporate-flight-bookings",
        "car-pooling",
    ],
    [
        "permutations",
        "combinations",
        "subsets",
        "combination-sum",
        "palindrome-partitioning",
        "n-queens",
        "open-the-lock",
        "word-ladder",
    ],
    [
        "maximum-depth-of-binary-tree",
        "diameter-of-binary-tree",
        "invert-binary-tree",
        "construct-binary-tree-from-preorder-and-inorder-traversal",
        "lowest-common-ancestor-of-a-binary-tree",
        "validate-binary-search-tree",
        "kth-smallest-element-in-a-bst",
        "serialize-and-deserialize-binary-tree",
    ],
    [
        "number-of-islands",
        "clone-graph",
        "course-schedule",
        "course-schedule-ii",
        "redundant-connection",
        "network-delay-time",
        "cheapest-flights-within-k-stops",
        "min-cost-to-connect-all-points",
    ],
    [
        "fibonacci-number",
        "coin-change",
        "partition-equal-subset-sum",
        "longest-common-subsequence",
        "edit-distance",
        "longest-increasing-subsequence",
        "house-robber",
        "best-time-to-buy-and-sell-stock-with-cooldown",
    ],
    [
        "daily-temperatures",
        "next-greater-element-ii",
        "largest-rectangle-in-histogram",
        "sliding-window-maximum",
        "kth-largest-element-in-an-array",
        "top-k-frequent-elements",
        "find-median-from-data-stream",
        "lru-cache",
    ],
    [
        "trapping-rain-water",
        "merge-intervals",
        "random-pick-with-weight",
        "reverse-nodes-in-k-group",
        "palindrome-linked-list",
        "insert-delete-getrandom-o1",
        "design-twitter",
        "basic-calculator",
    ],
]

TIH_THEME_FILES = [
    "array",
    "hash-table",
    "string",
    "sorting-searching",
    "linked-list",
    "stack",
    "queue",
    "recursion",
    "tree",
    "trie",
    "heap",
    "graph",
    "interval",
    "matrix",
    "dynamic-programming",
    "binary",
    "math",
    "geometry",
]


@dataclass(frozen=True)
class ProblemIndex:
    rows_by_slug: dict[str, dict[str, Any]]
    slug_by_frontend_display_id: dict[str, str]

    @classmethod
    def load(cls, path: Path) -> "ProblemIndex":
        return cls.from_rows(read_jsonl(path))

    @classmethod
    def from_rows(cls, rows: list[dict[str, Any]]) -> "ProblemIndex":
        rows_by_slug = {normalize_slug(str(row["slug"])): row for row in rows}
        slug_by_frontend_display_id = {
            normalize_display_id(str(row["frontendDisplayId"])): normalize_slug(str(row["slug"]))
            for row in rows
            if row.get("frontendDisplayId")
        }
        return cls(rows_by_slug, slug_by_frontend_display_id)

    def has_slug(self, slug: str) -> bool:
        return slug in self.rows_by_slug

    def title(self, slug: str) -> str:
        row = self.rows_by_slug.get(slug, {})
        return str(row.get("titleEn") or row.get("titleZh") or title_from_slug(slug))

    def difficulty(self, slug: str, fallback: str = "Medium") -> str:
        row = self.rows_by_slug.get(slug, {})
        value = str(row.get("difficulty") or fallback).strip().lower()
        return {"easy": "Easy", "medium": "Medium", "hard": "Hard"}.get(value, fallback)

    def frontend_id(self, slug: str) -> Any:
        return self.rows_by_slug.get(slug, {}).get("frontendId")

    def tags(self, slug: str) -> list[str]:
        return list(self.rows_by_slug.get(slug, {}).get("tagValues") or [])

    def resolve_slug(self, source_slug: str, frontend_display_id: str = "") -> str:
        slug = normalize_slug(source_slug)
        if self.has_slug(slug):
            return slug
        return self.slug_by_frontend_display_id.get(normalize_display_id(frontend_display_id), slug)


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate the selected P1-B template source directories.")
    parser.add_argument("--output-root", default=str(DEFAULT_OUTPUT_ROOT))
    parser.add_argument("--local-problems", default=str(DEFAULT_LOCAL_PROBLEMS_PATH))
    parser.add_argument("--tih-dir")
    parser.add_argument("--sean-questions")
    parser.add_argument("--sean-roadmaps")
    parser.add_argument("--doocs-lcof")
    parser.add_argument("--doocs-lcci")
    parser.add_argument("--leetcode-75")
    parser.add_argument("--top-interview-150")
    parser.add_argument("--leetcode-master-readme")
    parser.add_argument("--sql-50")
    parser.add_argument("--javascript-30-days")
    parser.add_argument("--pandas-introduction")
    parser.add_argument("--pandas-30-days")
    args = parser.parse_args()

    problem_index = ProblemIndex.load(Path(args.local_problems))
    tih_markdowns = load_tih_markdowns(Path(args.tih_dir) if args.tih_dir else None)
    sean_questions = load_json(Path(args.sean_questions) if args.sean_questions else None, f"{SEAN_RAW_BASE}/questions.json")
    sean_roadmaps = load_text(Path(args.sean_roadmaps) if args.sean_roadmaps else None, f"{SEAN_RAW_BASE}/roadmaps.ts")
    doocs_lcof = load_json(Path(args.doocs_lcof) if args.doocs_lcof else None, DOOCS_LCOF_URL)
    doocs_lcci = load_json(Path(args.doocs_lcci) if args.doocs_lcci else None, DOOCS_LCCI_URL)
    leetcode_75 = load_study_plan(Path(args.leetcode_75) if args.leetcode_75 else None, "leetcode-75")
    top_interview_150 = load_study_plan(
        Path(args.top_interview_150) if args.top_interview_150 else None,
        "top-interview-150",
    )
    leetcode_master_readme = load_text(
        Path(args.leetcode_master_readme) if args.leetcode_master_readme else None,
        LEETCODE_MASTER_README_URL,
    )
    sql_50 = load_study_plan(Path(args.sql_50) if args.sql_50 else None, "top-sql-50")
    javascript_30_days = load_study_plan(
        Path(args.javascript_30_days) if args.javascript_30_days else None,
        "30-days-of-javascript",
    )
    pandas_introduction = load_study_plan(
        Path(args.pandas_introduction) if args.pandas_introduction else None,
        "introduction-to-pandas",
    )
    pandas_30_days = load_study_plan(
        Path(args.pandas_30_days) if args.pandas_30_days else None,
        "30-days-of-pandas",
    )

    outputs = build_templates(
        problem_index,
        tih_markdowns,
        sean_questions,
        sean_roadmaps,
        doocs_lcof,
        doocs_lcci,
        leetcode_75,
        top_interview_150,
        leetcode_master_readme,
        sql_50,
        javascript_30_days,
        pandas_introduction,
        pandas_30_days,
    )
    write_template_sources(Path(args.output_root), outputs)
    for template_id in TEMPLATE_IDS:
        template, refs = outputs[template_id]
        matched = sum(1 for ref in refs if problem_index.has_slug(ref["problemSlug"]))
        print(
            f"{template_id}: phases={len(template['phases'])}, refs={len(refs)}, "
            f"matched={matched}, missing={len(refs) - matched}"
        )


def load_tih_markdowns(source_dir: Path | None) -> dict[str, str]:
    return {
        theme: load_text(source_dir / f"{theme}.md" if source_dir else None, f"{TIH_RAW_BASE}/{theme}.md")
        for theme in TIH_THEME_FILES
    }


def load_text(path: Path | None, url: str) -> str:
    if path:
        return path.read_text(encoding="utf-8")
    request = urllib.request.Request(url, headers={"User-Agent": "algo-mentor-template-import"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode("utf-8")


def load_json(path: Path | None, url: str) -> Any:
    return json.loads(load_text(path, url))


def load_study_plan(path: Path | None, plan_slug: str) -> dict[str, Any]:
    if path:
        payload = json.loads(path.read_text(encoding="utf-8"))
    else:
        body = json.dumps({
            "operationName": "studyPlanV2Detail",
            "variables": {"slug": plan_slug},
            "query": LEETCODE_STUDY_PLAN_QUERY,
        }).encode("utf-8")
        request = urllib.request.Request(
            LEETCODE_GRAPHQL_URL,
            data=body,
            headers={
                "Content-Type": "application/json",
                "Referer": f"https://leetcode.com/studyplan/{plan_slug}/",
                "User-Agent": "algo-mentor-template-import",
            },
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = json.loads(response.read().decode("utf-8"))
    detail = payload.get("data", {}).get("studyPlanV2Detail") if "data" in payload else payload
    if not isinstance(detail, dict) or not detail.get("planSubGroups"):
        raise ValueError(f"missing study plan data: {plan_slug}")
    return detail


def build_templates(
    problem_index: ProblemIndex,
    tih_markdowns: dict[str, str],
    sean_questions: Any,
    sean_roadmaps: str,
    doocs_lcof: Any,
    doocs_lcci: Any,
    leetcode_75: dict[str, Any],
    top_interview_150: dict[str, Any],
    leetcode_master_readme: str,
    sql_50: dict[str, Any],
    javascript_30_days: dict[str, Any],
    pandas_introduction: dict[str, Any],
    pandas_30_days: dict[str, Any],
) -> dict[str, tuple[dict[str, Any], list[dict[str, Any]]]]:
    outputs = {
        "tih_algorithm_essentials": build_tih_template(problem_index, tih_markdowns),
        "topic_dp_advanced": build_dp_advanced_template(problem_index),
        "sword_offer_classic": build_sword_offer_template(problem_index, list(doocs_lcof)),
        "leetcode_patterns_beginner_roadmap": build_sean_beginner_template(
            problem_index,
            sean_questions,
            sean_roadmaps,
        ),
        "cracking_coding_interview_classic": build_lcci_template(problem_index, list(doocs_lcci)),
        "leetcode_75_core_sprint": build_leetcode_75_template(problem_index, leetcode_75),
        "leetcode_top_interview_150": build_top_interview_150_template(problem_index, top_interview_150),
        "carl_algorithm_roadmap_full": build_carl_algorithm_roadmap_template(
            problem_index,
            leetcode_master_readme,
        ),
        "labuladong_algo_thinking": build_labuladong_algo_thinking_template(problem_index),
        "leetcode_sql_50": build_sql_50_template(problem_index, sql_50),
        "leetcode_javascript_30_days": build_javascript_30_days_template(
            problem_index,
            javascript_30_days,
        ),
        "leetcode_pandas_introduction": build_pandas_introduction_template(
            problem_index,
            pandas_introduction,
        ),
        "leetcode_pandas_30_days": build_pandas_30_days_template(problem_index, pandas_30_days),
    }
    validate_outputs(outputs)
    return outputs


def build_tih_template(
    problem_index: ProblemIndex,
    markdowns: dict[str, str],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    questions, slugs_by_theme = parse_tih_algorithm_questions(markdowns)
    phase_specs = [
        phase_spec(
            "线性结构与查找基础",
            "集中训练数组、哈希、字符串与排序搜索中的面试核心模型。",
            ["Array", "Hash Table", "String", "Sorting", "Binary Search"],
            ["array", "hash-table", "string", "sorting-searching"],
        ),
        phase_spec(
            "链表、栈队列与递归",
            "建立指针改写、栈队列状态和递归分解的稳定解题模板。",
            ["Linked List", "Stack", "Queue", "Recursion"],
            ["linked-list", "stack", "queue", "recursion"],
        ),
        phase_spec(
            "树、Trie 与优先队列",
            "覆盖树遍历、二叉搜索树、前缀树和堆的高频面试题。",
            ["Tree", "Trie", "Heap"],
            ["tree", "trie", "heap"],
        ),
        phase_spec(
            "图、区间与矩阵建模",
            "训练图搜索、依赖关系、区间边界和二维矩阵遍历。",
            ["Graph", "Intervals", "Matrix"],
            ["graph", "interval", "matrix"],
        ),
        phase_spec(
            "动态规划与二进制技巧",
            "用状态定义、转移顺序和位运算完成高频综合题。",
            ["Dynamic Programming", "Bit Manipulation"],
            ["dynamic-programming", "binary"],
        ),
        phase_spec(
            "数学与几何综合收尾",
            "补齐数值规律、坐标关系和几何边界，完成整套题型复盘。",
            ["Math", "Geometry"],
            ["math", "geometry"],
        ),
    ]
    refs: list[dict[str, Any]] = []
    assigned: set[str] = set()
    source_order = 0
    for phase_index, spec in enumerate(phase_specs, start=1):
        sort_order = 0
        for theme in spec.pop("sourceThemes"):
            for slug in slugs_by_theme[theme]:
                if slug in assigned:
                    continue
                assigned.add(slug)
                source_order += 1
                sort_order += 1
                question = questions[slug]
                refs.append(problem_ref(
                    "tih_algorithm_essentials",
                    phase_index,
                    sort_order,
                    source_order,
                    slug,
                    question["title"],
                    problem_index.difficulty(slug),
                    spec["title"],
                    leetcode_url(slug),
                    {
                        "sourceKey": "tih",
                        "sourceName": TIH_SOURCE_NAME,
                        "sourceLicense": "MIT",
                        "sourceTheme": question["themes"][0],
                        "sourceThemes": question["themes"],
                        "sourceSections": question["sections"],
                        "sourceStrategy": "direct_algorithm_essential_metadata",
                    },
                ))
    template = template_row(
        template_id="tih_algorithm_essentials",
        title="算法面试核心专题计划",
        summary="用 6 周覆盖 Tech Interview Handbook 的核心算法主题，形成可复用的面试题型地图。",
        goal="系统掌握算法面试中常见数据结构、搜索、动态规划和综合建模方法。",
        intent="TOPIC_BREAKTHROUGH",
        level="INTERMEDIATE",
        duration_weeks=6,
        weekly_hours=10,
        difficulty_preference="MIXED",
        target_audience="已有基础刷题经验，希望按主题系统补齐算法面试知识结构的学习者。",
        prerequisites=["掌握数组、链表、树和图的基本概念", "能独立完成常见 Easy 和部分 Medium 题"],
        recommended_for=["需要系统查漏补缺", "希望建立面试题型选择框架", "准备中期算法面试"],
        not_recommended_for=["完全没有数据结构基础", "只剩一周进行极限冲刺"],
        expected_outcome="完成后能根据题目特征快速定位核心数据结构和算法，并形成覆盖主要面试专题的复盘清单。",
        source_name=TIH_SOURCE_NAME,
        source_url=TIH_SOURCE_URL,
        source_commit=TIH_COMMIT,
        source_data_path="apps/website/contents/algorithms/*.md",
        source_description="Tech Interview Handbook 算法主题页中的 Essential 和 Recommended practice 题目元数据。",
        curation_notes="按主题首次出现位置去重，再重组为 6 个可执行阶段；只保留题名、slug、主题和来源区段。",
        license_notice="MIT License；内部 seed 仅使用结构化题目元数据，不包含文章正文或题解。",
        phases=phase_specs,
        metadata={
            "sourceProblemCount": len(refs),
            "sourceThemeCount": len(TIH_THEME_FILES),
            "sourceStrategy": "direct_algorithm_essential_metadata",
        },
    )
    return template, refs


def parse_tih_algorithm_questions(
    markdowns: dict[str, str],
) -> tuple[dict[str, dict[str, Any]], dict[str, list[str]]]:
    questions: dict[str, dict[str, Any]] = {}
    slugs_by_theme: dict[str, list[str]] = {theme: [] for theme in TIH_THEME_FILES}
    accepted_headings = {
        "## Essential questions": "essential",
        "## Recommended practice questions": "recommended",
    }
    pattern = re.compile(r"\[([^]]+)]\(https://leetcode\.com/problems/([^/)]+)(?:/[^)]*)?\)")
    for theme in TIH_THEME_FILES:
        active_section = ""
        for line in markdowns[theme].splitlines():
            stripped = line.strip()
            if stripped.startswith("## "):
                active_section = accepted_headings.get(stripped, "")
                continue
            if not active_section:
                continue
            match = pattern.search(line)
            if not match:
                continue
            title = clean_markdown(match.group(1))
            slug = normalize_slug(match.group(2))
            question = questions.setdefault(slug, {
                "title": title,
                "themes": [],
                "sections": [],
            })
            if theme not in question["themes"]:
                question["themes"].append(theme)
            if active_section not in question["sections"]:
                question["sections"].append(active_section)
            if slug not in slugs_by_theme[theme]:
                slugs_by_theme[theme].append(slug)
    return questions, slugs_by_theme


def build_dp_advanced_template(problem_index: ProblemIndex) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    phase_specs = [
        phase_spec(
            "区间动态规划",
            "训练以区间长度、左右端点和最后一次操作为核心的状态设计。",
            ["Dynamic Programming", "Interval DP", "String"],
            [
                "longest-palindromic-subsequence",
                "minimum-insertion-steps-to-make-a-string-palindrome",
                "strange-printer",
                "burst-balloons",
                "minimum-cost-to-cut-a-stick",
                "remove-boxes",
                "stone-game-vii",
                "minimum-score-triangulation-of-polygon",
            ],
        ),
        phase_spec(
            "树形动态规划",
            "通过子树返回值、选或不选状态和换根技巧解决树上最优问题。",
            ["Dynamic Programming", "Tree", "Depth First Search"],
            [
                "house-robber-iii",
                "binary-tree-cameras",
                "maximum-sum-bst-in-binary-tree",
                "longest-zigzag-path-in-a-binary-tree",
                "distribute-coins-in-binary-tree",
                "binary-tree-maximum-path-sum",
                "sum-of-distances-in-tree",
            ],
        ),
        phase_spec(
            "状压与子集动态规划",
            "用位掩码表达集合状态，并控制状态数量、转移顺序和记忆化边界。",
            ["Dynamic Programming", "Bitmask", "Memoization"],
            [
                "partition-to-k-equal-sum-subsets",
                "matchsticks-to-square",
                "shortest-path-visiting-all-nodes",
                "minimum-xor-sum-of-two-arrays",
                "maximum-compatibility-score-sum",
                "number-of-ways-to-wear-different-hats-to-each-other",
                "parallel-courses-ii",
                "can-i-win",
            ],
        ),
        phase_spec(
            "数位、计数与博弈动态规划",
            "综合练习高位约束、计数状态和双方最优选择下的状态转移。",
            ["Dynamic Programming", "Digit DP", "Game Theory", "Math"],
            [
                "number-of-digit-one",
                "numbers-at-most-n-given-digit-set",
                "count-special-integers",
                "count-of-integers",
                "non-negative-integers-without-consecutive-ones",
                "stone-game",
                "stone-game-ii",
                "stone-game-iii",
            ],
        ),
    ]
    refs: list[dict[str, Any]] = []
    source_order = 0
    for phase_index, spec in enumerate(phase_specs, start=1):
        slugs = spec.pop("sourceThemes")
        for sort_order, slug in enumerate(slugs, start=1):
            source_order += 1
            refs.append(problem_ref(
                "topic_dp_advanced",
                phase_index,
                sort_order,
                source_order,
                slug,
                problem_index.title(slug),
                problem_index.difficulty(slug),
                spec["title"],
                leetcode_url(slug),
                {
                    "sourceFrontendId": problem_index.frontend_id(slug),
                    "sourceKey": "algo_mentor_local_problem_seed",
                    "sourceLicense": "project-local",
                    "sourceName": LOCAL_SOURCE_NAME,
                    "sourceStrategy": "local_advanced_dp_curation",
                    "sourceTags": problem_index.tags(slug),
                },
            ))
    template = template_row(
        template_id="topic_dp_advanced",
        title="动态规划进阶专项计划",
        summary="4 周突破区间、树形、状压、数位与博弈动态规划，补齐基础 DP 之后的进阶模型。",
        goal="建立复杂动态规划的状态设计、转移优化和问题分类能力。",
        intent="TOPIC_BREAKTHROUGH",
        level="ADVANCED",
        duration_weeks=4,
        weekly_hours=9,
        difficulty_preference="HARD",
        target_audience="已完成动态规划基础专题，能独立写出常见一维、背包和子序列 DP 的学习者。",
        prerequisites=["掌握状态定义、转移方程和初始化", "熟悉递归、记忆化搜索、树遍历和位运算"],
        recommended_for=["基础 DP 已掌握但 Hard 题缺少分类框架", "需要突破区间、树形、状压或数位 DP"],
        not_recommended_for=["尚未完成动态规划基础专题", "只需要 Easy 级语法练习"],
        expected_outcome="完成后能识别四类进阶 DP 的状态维度，独立设计转移并复盘时间与空间复杂度。",
        source_name=LOCAL_SOURCE_NAME,
        source_url="data/seed/problems.jsonl",
        source_commit=LOCAL_SOURCE_COMMIT,
        source_data_path="data/seed/problems.jsonl",
        source_description="从项目本地题库中按 dynamic-programming、bitmask、tree 和 game-theory 标签精选的进阶路线。",
        curation_notes="题目按区间、树形、状压、数位与博弈四类模型整理，不与基础 DP 模板重复使用同一批入门题。",
        license_notice="项目内部题库结构化元数据；模板不包含题面、题解或代码。",
        phases=phase_specs,
        metadata={"sourceProblemCount": len(refs), "sourceStrategy": "local_advanced_dp_curation"},
    )
    return template, refs


def build_sword_offer_template(
    problem_index: ProblemIndex,
    rows: list[dict[str, Any]],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    ordered_rows = sorted(rows, key=sword_offer_order)
    phase_specs = [
        sword_phase("数据结构与查找入门", "从数组、链表、树重建、栈队列和基础搜索开始建立经典题手感。", 3, 12, ["Array", "Linked List", "Tree", "Stack"]),
        sword_phase("递归、数值与边界", "训练递归拆分、动态规划、位运算和数值边界处理。", 13, 21, ["Recursion", "Dynamic Programming", "Math", "Bit Manipulation"]),
        sword_phase("链表、树与栈队列", "集中处理指针改写、树结构判断、矩阵模拟和带最值的数据结构。", 22, 30, ["Linked List", "Tree", "Stack", "Matrix"]),
        sword_phase("树遍历、复制与回溯", "完成树的层序与深度遍历、复杂链表复制、序列化和排列搜索。", 31, 38, ["Tree", "Breadth First Search", "Depth First Search", "Backtracking"]),
        sword_phase("数组统计、堆与连续状态", "训练多数元素、Top K、数据流中位数、连续子数组和数字规律。", 39, 46, ["Array", "Heap", "Dynamic Programming", "Math"]),
        sword_phase("动态规划、哈希与归并", "覆盖网格 DP、滑动窗口、哈希计数、逆序对和链表交点。", 47, 53, ["Dynamic Programming", "Hash Table", "Sliding Window", "Merge Sort"]),
        sword_phase("树、位运算与窗口综合", "补齐 BST、树深度、位运算计数、双指针、字符串和单调队列。", 54, 59, ["Tree", "Bit Manipulation", "Two Pointers", "Sliding Window"]),
        sword_phase("概率、数学与综合收尾", "用概率、约瑟夫环、股票 DP、位运算和最近公共祖先完成路线收尾。", 60, 68, ["Math", "Dynamic Programming", "Bit Manipulation", "Tree"]),
    ]
    refs: list[dict[str, Any]] = []
    source_order = 0
    sort_order_by_phase: dict[int, int] = {}
    for row in ordered_rows:
        question_number = sword_offer_number(str(row["frontend_id"]))
        phase_index = next(
            index
            for index, spec in enumerate(phase_specs, start=1)
            if spec["sourceRange"][0] <= question_number <= spec["sourceRange"][1]
        )
        source_order += 1
        sort_order_by_phase[phase_index] = sort_order_by_phase.get(phase_index, 0) + 1
        slug = slug_from_url(str(row["url_cn"]))
        refs.append(problem_ref(
            "sword_offer_classic",
            phase_index,
            sort_order_by_phase[phase_index],
            source_order,
            slug,
            str(row.get("title_cn") or row.get("title") or problem_index.title(slug)),
            str(row.get("difficulty") or problem_index.difficulty(slug)),
            phase_specs[phase_index - 1]["title"],
            str(row["url_cn"]),
            {
                "sourceFrontendId": str(row["frontend_id"]),
                "sourceKey": "doocs",
                "sourceLicense": "CC-BY-SA-4.0",
                "sourceName": DOOCS_SOURCE_NAME,
                "sourceStrategy": "direct_lcof_problem_metadata",
                "sourceTags": list(row.get("tags") or []),
            },
        ))
    for spec in phase_specs:
        spec.pop("sourceThemes", None)
        spec.pop("sourceRange", None)
    template = template_row(
        template_id="sword_offer_classic",
        title="剑指 Offer 经典面试计划",
        summary="用 8 周完成剑指 Offer 经典题路线，系统训练中文面试中的数据结构、算法和边界处理。",
        goal="按经典题序建立稳定的中文算法面试知识脉络和复盘材料。",
        intent="INTERVIEW_SPRINT",
        level="INTERMEDIATE",
        duration_weeks=8,
        weekly_hours=8,
        difficulty_preference="MIXED",
        target_audience="偏好中文题单、希望系统复习经典校招和社招算法题的学习者。",
        prerequisites=["掌握一种编程语言", "了解数组、链表、树、栈队列和基础动态规划"],
        recommended_for=["准备中文技术面试", "希望按经典题序系统查漏补缺", "需要 6 到 8 周完整路线"],
        not_recommended_for=["完全没有编程基础", "只需要单一专题的短期突破"],
        expected_outcome="完成后能独立处理剑指 Offer 的主要题型，并形成适合中文面试表达的解法、复杂度和边界复盘。",
        source_name=DOOCS_SOURCE_NAME,
        source_url=DOOCS_SOURCE_URL,
        source_commit=DOOCS_COMMIT,
        source_data_path="lcof/lcof.json",
        source_description="doocs/leetcode 剑指 Offer 结构化题目清单，包含题号、标题、难度、标签和 LeetCode CN slug。",
        curation_notes="按剑指 Offer 题号升序保留完整路线，并按连续题号划分为 8 个可执行阶段。",
        license_notice="CC-BY-SA-4.0；内部 seed 仅使用结构化题目元数据，并保留来源与版本信息。",
        phases=phase_specs,
        metadata={"sourceProblemCount": len(refs), "sourceStrategy": "direct_lcof_problem_metadata"},
    )
    return template, refs


def sword_phase(title: str, focus: str, start: int, end: int, tags: list[str]) -> dict[str, Any]:
    spec = phase_spec(title, focus, tags, (start, end))
    spec["sourceRange"] = (start, end)
    return spec


def build_lcci_template(
    problem_index: ProblemIndex,
    rows: list[dict[str, Any]],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    phase_specs = [
        phase_spec("字符串与数组", "训练字符串判定、矩阵操作、字符计数和基础模拟。", ["String", "Array", "Matrix"], ["01"]),
        phase_spec("链表、栈与队列", "集中处理链表改写、环与交点，以及栈队列的状态设计。", ["Linked List", "Stack", "Queue", "Design"], ["02", "03"]),
        phase_spec("树与图", "覆盖树遍历、二叉搜索树、公共祖先、路径和与图搜索。", ["Tree", "Graph", "Depth First Search", "Breadth First Search"], ["04"]),
        phase_spec("位运算与递归基础", "先完成位操作，再进入递归、子集、排列和基础动态规划。", ["Bit Manipulation", "Recursion", "Backtracking", "Dynamic Programming"], ["05", "08.01-08.07"]),
        phase_spec("回溯、动态规划与排序搜索", "完成递归章节后半段，并衔接排序、二分和矩阵搜索。", ["Backtracking", "Dynamic Programming", "Sorting", "Binary Search"], ["08.08-08.14", "10"]),
        phase_spec("中等题：数值、数组与几何", "训练数值边界、数组统计、几何和不使用额外运算符的设计题。", ["Math", "Array", "Geometry", "Design"], ["16.01-16.13"]),
        phase_spec("中等题：字符串、搜索与设计", "覆盖字符串匹配、网格搜索、缓存、表达式和综合模拟。", ["String", "Search", "Design", "Hash Table"], ["16.14-16.26"]),
        phase_spec("困难题：位运算、统计与图", "用高阶位运算、计数、并查集和动态规划进入困难题训练。", ["Bit Manipulation", "Math", "Graph", "Dynamic Programming"], ["17.01-17.13"]),
        phase_spec("困难题：搜索、字符串与窗口", "训练 Top K、字典树、多模式搜索和最短窗口类问题。", ["Heap", "Trie", "String", "Sliding Window"], ["17.14-17.19"]),
        phase_spec("困难题：堆、矩阵与综合设计", "以连续中值、矩阵动态规划、图搜索和综合设计完成路线收尾。", ["Heap", "Dynamic Programming", "Graph", "Design"], ["17.20-17.26"]),
    ]
    refs: list[dict[str, Any]] = []
    sort_order_by_phase: dict[int, int] = {}
    for source_order, row in enumerate(sorted(rows, key=lcci_order), start=1):
        chapter, question = lcci_order(row)
        phase_index = lcci_phase_index(chapter, question)
        sort_order_by_phase[phase_index] = sort_order_by_phase.get(phase_index, 0) + 1
        source_slug = slug_from_url(str(row["url_cn"]))
        frontend_display_id = str(row["frontend_id"])
        slug = problem_index.resolve_slug(source_slug, frontend_display_id)
        refs.append(problem_ref(
            "cracking_coding_interview_classic",
            phase_index,
            sort_order_by_phase[phase_index],
            source_order,
            slug,
            str(row.get("title_cn") or row.get("title") or problem_index.title(slug)),
            str(row.get("difficulty") or problem_index.difficulty(slug)),
            phase_specs[phase_index - 1]["title"],
            f"https://leetcode.cn/problems/{slug}/",
            {
                "matchedBy": "slug" if source_slug == slug else "frontendDisplayId",
                "sourceFrontendId": frontend_display_id,
                "sourceKey": "doocs",
                "sourceLicense": "CC-BY-SA-4.0",
                "sourceName": DOOCS_SOURCE_NAME,
                "sourceOriginalSlug": source_slug,
                "sourceOriginalUrl": str(row["url_cn"]),
                "sourceStrategy": "direct_lcci_problem_metadata",
                "sourceTags": list(row.get("tags") or []),
            },
        ))
    for spec in phase_specs:
        spec.pop("sourceThemes", None)
    template = template_row(
        template_id="cracking_coding_interview_classic",
        title="程序员面试金典系统训练计划",
        summary="用 10 周完成程序员面试金典 109 题路线，覆盖字符串、链表、树图、位运算、递归和综合难题。",
        goal="按面试金典章节顺序建立从基础数据结构到综合难题的系统面试能力。",
        intent="INTERVIEW_SPRINT",
        level="INTERMEDIATE",
        duration_weeks=10,
        weekly_hours=10,
        difficulty_preference="MIXED",
        target_audience="已有数据结构基础，希望系统训练经典程序员面试题并逐步进入综合难题的学习者。",
        prerequisites=["掌握一种主力编程语言", "理解数组、链表、树、图和递归基础", "能独立完成常见 Easy 和部分 Medium 题"],
        recommended_for=["准备中文技术面试", "希望按经典章节系统训练", "需要覆盖基础题和综合难题"],
        not_recommended_for=["完全没有编程基础", "只剩一周进行极限冲刺", "只需要单一专题训练"],
        expected_outcome="完成后能按章节识别高频面试模型，并形成覆盖基础结构、递归搜索和综合设计题的完整复盘材料。",
        source_name=DOOCS_SOURCE_NAME,
        source_url=DOOCS_SOURCE_URL,
        source_commit=DOOCS_COMMIT,
        source_data_path="lcci/lcci.json",
        source_description="doocs/leetcode 程序员面试金典结构化清单，包含题号、标题、难度、标签和 LeetCode CN slug。",
        curation_notes="按面试题章节和题号升序保留完整 109 题路线；章节 16 和 17 按题号区间拆分，来源拼写错误的 05.02 slug 通过 frontendDisplayId 归一化。",
        license_notice="CC-BY-SA-4.0；内部 seed 仅使用结构化题目元数据，并保留来源与版本信息。",
        phases=phase_specs,
        metadata={"sourceProblemCount": len(refs), "sourceStrategy": "direct_lcci_problem_metadata"},
    )
    return template, refs


def build_leetcode_75_template(
    problem_index: ProblemIndex,
    detail: dict[str, Any],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    phases = [
        official_phase("数组、双指针与窗口", "从数组和字符串开始，训练双指针、滑动窗口与前缀和。", ["Array", "String", "Two Pointers", "Sliding Window", "Prefix Sum"], ["Array / String", "Two Pointers", "Sliding Window", "Prefix Sum"]),
        official_phase("哈希、栈队列与链表", "建立哈希映射、栈队列状态和链表指针改写的稳定模板。", ["Hash Table", "Stack", "Queue", "Linked List"], ["Hash Map / Set", "Stack", "Queue", "Linked List"]),
        official_phase("二叉树与搜索树", "集中训练树的 DFS、BFS 和二叉搜索树性质。", ["Tree", "Depth First Search", "Breadth First Search", "Binary Search Tree"], ["Binary Tree - DFS", "Binary Tree - BFS", "Binary Search Tree"]),
        official_phase("图、堆、二分与回溯", "覆盖图搜索、优先队列、边界二分和基础回溯。", ["Graph", "Heap", "Binary Search", "Backtracking"], ["Graphs - DFS", "Graphs - BFS", "Heap / Priority Queue", "Binary Search", "Backtracking"]),
        official_phase("一维与多维动态规划", "通过一维和多维状态设计完成动态规划主线，并补齐位运算。", ["Dynamic Programming", "Bit Manipulation"], ["DP - 1D", "DP - Multidimensional", "Bit Manipulation"]),
        official_phase("Trie、区间与单调栈收尾", "用前缀树、区间和单调栈完成专项收尾与整套路线复盘。", ["Trie", "Intervals", "Monotonic Stack"], ["Trie", "Intervals", "Monotonic Stack"]),
    ]
    return build_official_study_plan_template(
        problem_index=problem_index,
        detail=detail,
        template_id="leetcode_75_core_sprint",
        plan_slug="leetcode-75",
        title="LeetCode 75 核心冲刺计划",
        summary="用 6 周完成 LeetCode 75 官方路线，覆盖 22 类核心面试主题。",
        goal="以题量适中的官方路线建立主流面试题型覆盖和稳定完成体验。",
        duration_weeks=6,
        weekly_hours=9,
        target_audience="已有基础数据结构知识，希望用 1 到 2 个月完成高质量面试核心路线的学习者。",
        prerequisites=["掌握一种主力编程语言", "了解数组、链表、树和图的基础概念"],
        recommended_for=["需要中短期面试冲刺", "希望使用官方 75 题路线", "不想直接进入 150 题长计划"],
        not_recommended_for=["完全没有数据结构基础", "已经完整掌握 LeetCode 75", "只需要单一专题突破"],
        expected_outcome="完成后能识别 22 类核心题型，并对常见数据结构、图搜索和动态规划题形成稳定解题模板。",
        phases=phases,
    )


def build_top_interview_150_template(
    problem_index: ProblemIndex,
    detail: dict[str, Any],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    phases = [
        official_phase("数组与字符串", "完成官方数组与字符串主线，稳定原地修改、模拟和边界处理。", ["Array", "String"], ["Array / String"]),
        official_phase("双指针、窗口与矩阵", "训练双指针、滑动窗口和二维矩阵中的状态维护。", ["Two Pointers", "Sliding Window", "Matrix"], ["Two Pointers", "Sliding Window", "Matrix"]),
        official_phase("哈希、区间与栈", "覆盖映射计数、区间操作和栈结构的高频面试模型。", ["Hash Table", "Intervals", "Stack"], ["Hashmap", "Intervals", "Stack"]),
        official_phase("链表", "系统完成链表改写、环、复制、排序和缓存设计。", ["Linked List", "Design"], ["Linked List"]),
        official_phase("二叉树与搜索树", "训练树的递归、层序遍历和二叉搜索树性质。", ["Tree", "Breadth First Search", "Binary Search Tree"], ["Binary Tree General", "Binary Tree BFS", "Binary Search Tree"]),
        official_phase("图与 Trie", "完成图遍历、拓扑关系、最短路径前置和前缀树。", ["Graph", "Breadth First Search", "Trie"], ["Graph General", "Graph BFS", "Trie"]),
        official_phase("回溯与分治", "训练选择树、剪枝、递归分解和归并式问题求解。", ["Backtracking", "Divide and Conquer"], ["Backtracking", "Divide & Conquer"]),
        official_phase("Kadane、二分与堆", "用连续状态、边界搜索和优先队列处理高频综合题。", ["Dynamic Programming", "Binary Search", "Heap"], ["Kadane's Algorithm", "Binary Search", "Heap"]),
        official_phase("位运算与数学", "补齐位操作、数值规律、几何和进制相关面试题。", ["Bit Manipulation", "Math"], ["Bit Manipulation", "Math"]),
        official_phase("动态规划综合收尾", "完成一维和多维动态规划，并进行整套路线复盘。", ["Dynamic Programming"], ["1D DP", "Multidimensional DP"]),
    ]
    return build_official_study_plan_template(
        problem_index=problem_index,
        detail=detail,
        template_id="leetcode_top_interview_150",
        plan_slug="top-interview-150",
        title="LeetCode 面试经典 150 题计划",
        summary="用 10 周完成 LeetCode Top Interview 150 官方路线，系统覆盖经典面试数据结构与算法。",
        goal="按官方 150 题主题顺序完成一轮高密度、完整的算法面试训练。",
        duration_weeks=10,
        weekly_hours=12,
        target_audience="有稳定刷题时间，希望按官方经典 150 题路线进行系统面试备战的学习者。",
        prerequisites=["掌握一种主力编程语言", "理解常见数据结构和复杂度", "每周能稳定投入 10 小时以上"],
        recommended_for=["准备中大型技术面试", "希望覆盖官方经典 150 题", "已有少量刷题经验但缺少完整路线"],
        not_recommended_for=["只剩一到两周准备时间", "完全没有数据结构基础", "只需要单一专题训练"],
        expected_outcome="完成后能系统处理数组、链表、树图、动态规划和综合设计题，并形成完整的面试复盘清单。",
        phases=phases,
    )


def build_carl_algorithm_roadmap_template(
    problem_index: ProblemIndex,
    readme: str,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    sections = parse_carl_problem_sections(readme, problem_index)
    phase_specs = [
        phase_spec("数组基础与边界", "从数组存储、二分边界、双指针和滑动窗口建立线性结构基础。", ["Array", "Binary Search", "Sliding Window"], ["数组"]),
        phase_spec("链表指针操作", "集中训练链表增删、反转、相交和环检测。", ["Linked List", "Two Pointers"], ["链表"]),
        phase_spec("哈希表与组合查找", "掌握哈希集合、映射计数和多数求和问题中的去重策略。", ["Hash Table", "Array"], ["哈希表"]),
        phase_spec("字符串与双指针", "完成字符串原地修改、KMP 和数组链表中的双指针模型。", ["String", "Two Pointers"], ["字符串", "双指针法"]),
        phase_spec("栈、队列与优先级", "建立栈队列转换、表达式处理、单调结构和 Top K 基础。", ["Stack", "Queue", "Heap"], ["栈与队列"]),
        phase_spec("二叉树与搜索树", "系统训练树的遍历、路径、构造、公共祖先和 BST 性质。", ["Tree", "Binary Tree", "Binary Search Tree"], ["二叉树"]),
        phase_spec("回溯搜索", "按组合、切割、子集、排列和棋盘问题掌握选择树与剪枝。", ["Backtracking", "Depth First Search"], ["回溯算法"]),
        phase_spec("贪心策略", "通过序列、区间、跳跃和分配问题训练局部最优到全局最优的证明。", ["Greedy", "Intervals"], ["贪心算法"]),
        phase_spec("动态规划主线", "从基础状态转移推进到背包、打家劫舍、股票、子序列和编辑距离。", ["Dynamic Programming", "Knapsack", "Subsequence"], ["动态规划"]),
        phase_spec("单调栈综合", "用单调栈处理下一个更大元素、温度、接雨水和柱状图面积。", ["Monotonic Stack", "Stack"], ["单调栈"]),
        phase_spec("图论完整路线", "覆盖图遍历、岛屿、拓扑排序、并查集、最短路和最小生成树。", ["Graph", "Breadth First Search", "Depth First Search", "Union Find"], ["图论"]),
    ]
    durations = [2, 1, 1, 1, 1, 2, 1, 1, 3, 1, 2]
    for phase, duration in zip(phase_specs, durations, strict=True):
        phase["durationWeeks"] = duration

    refs: list[dict[str, Any]] = []
    seen: set[str] = set()
    source_order = 0
    for phase_index, phase in enumerate(phase_specs, start=1):
        sort_order = 0
        source_sections = phase.pop("sourceThemes")
        for section_name in source_sections:
            for item in sections[section_name]:
                slug = item["slug"]
                if slug in seen:
                    continue
                seen.add(slug)
                source_order += 1
                sort_order += 1
                refs.append(problem_ref(
                    "carl_algorithm_roadmap_full",
                    phase_index,
                    sort_order,
                    source_order,
                    slug,
                    problem_index.title(slug),
                    problem_index.difficulty(slug),
                    section_name,
                    leetcode_url(slug),
                    {
                        "sourceKey": "leetcode_master_reference",
                        "sourceName": LEETCODE_MASTER_SOURCE_NAME,
                        "sourceSection": section_name,
                        "sourcePath": item["path"],
                        "sourceStrategy": "reference_route_local_problem_rebuild",
                        "sourceTags": problem_index.tags(slug),
                    },
                ))

    template = template_row(
        template_id="carl_algorithm_roadmap_full",
        title="代码随想录完整刷题路线",
        summary="用 16 周按数组、链表、哈希、树、回溯、贪心、动态规划和图论完成中文系统刷题路线。",
        goal="沿清晰的知识依赖顺序完成一轮覆盖主要数据结构与算法的系统训练。",
        intent="LONG_TERM_LEARNING",
        level="BEGINNER",
        duration_weeks=16,
        weekly_hours=9,
        difficulty_preference="MIXED",
        target_audience="希望按中文知识脉络长期学习、已经掌握一门编程语言基础但缺少系统刷题顺序的学习者。",
        prerequisites=["掌握一种主力编程语言的基础语法", "每周能稳定投入 6 小时以上"],
        recommended_for=["第一次系统刷题", "希望补齐数据结构和算法知识图谱", "偏好中文路线和长期节奏"],
        not_recommended_for=["只剩一到两周面试冲刺", "已经完整刷过同一条代码随想录路线", "只需要单一薄弱专题"],
        expected_outcome="完成后能按知识依赖识别常见题型，形成覆盖线性结构、树图、搜索、贪心和动态规划的完整复盘体系。",
        source_name=LEETCODE_MASTER_SOURCE_NAME,
        source_url=LEETCODE_MASTER_SOURCE_URL,
        source_commit=LEETCODE_MASTER_COMMIT,
        source_data_path="README.md#刷题总目录",
        source_description="代码随想录固定版本 README 中按知识模块组织的刷题总目录。",
        curation_notes="按总目录模块顺序提取 LeetCode 题号，用本地题库 frontendDisplayId 归一化；跨模块重复题只保留首次出现，排除非 LeetCode 和文章型任务。",
        license_notice="仓库未提供 LICENSE；内部模板仅参考目录顺序和题号，并使用本地题库重建题目元数据。",
        phases=phase_specs,
        metadata={"sourceProblemCount": len(refs), "sourceStrategy": "reference_route_local_problem_rebuild"},
    )
    return template, refs


def parse_carl_problem_sections(readme: str, problem_index: ProblemIndex) -> dict[str, list[dict[str, str]]]:
    blocks = {
        clean_markdown(name): body
        for name, body in re.findall(
            r"<details>\s*<summary><b>(.*?)</b></summary>(.*?)</details>",
            readme,
            re.DOTALL,
        )
    }
    sections: dict[str, list[dict[str, str]]] = {}
    for section_name in CARL_SECTION_ORDER:
        if section_name not in blocks:
            raise ValueError(f"missing leetcode-master section: {section_name}")
        rows: list[dict[str, str]] = []
        for path in re.findall(r"\]\((\./problems/[^)]+\.md)\)", blocks[section_name]):
            display_id = carl_display_id(path)
            if not display_id:
                continue
            slug = problem_index.resolve_slug("", display_id)
            if not problem_index.has_slug(slug):
                raise ValueError(f"missing local problem for leetcode-master path: {path}")
            rows.append({"path": path.removeprefix("./"), "slug": slug})
        sections[section_name] = rows
    return sections


def carl_display_id(path: str) -> str:
    filename = path.rsplit("/", 1)[-1]
    numeric = re.match(r"(\d{4})\.", filename)
    if numeric:
        return str(int(numeric.group(1)))
    interview = re.match(r"面试题(\d{2})\.(\d{2})\.", filename)
    if interview:
        return f"面试题 {interview.group(1)}.{interview.group(2)}"
    sword_offer = re.match(r"剑指Offer(\d+)(?:-([A-Z]+))?\.", filename)
    if sword_offer:
        suffix = f" - {sword_offer.group(2)}" if sword_offer.group(2) else ""
        return f"剑指 Offer {int(sword_offer.group(1)):02d}{suffix}"
    return ""


def build_labuladong_algo_thinking_template(
    problem_index: ProblemIndex,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    phase_specs = [
        phase_spec("线性结构与双指针框架", "从数组和链表的遍历不变量出发，掌握快慢指针、左右指针和原地修改。", ["Array", "Linked List", "Two Pointers"], LABULADONG_PHASE_SLUGS[0]),
        phase_spec("窗口、二分与区间预处理", "训练滑动窗口、二分边界、前缀和与差分数组的通用框架。", ["Sliding Window", "Binary Search", "Prefix Sum", "Difference Array"], LABULADONG_PHASE_SLUGS[1]),
        phase_spec("递归、回溯与 BFS", "用决策树、状态恢复和最短步数模型统一理解递归搜索。", ["Backtracking", "Breadth First Search", "Recursion"], LABULADONG_PHASE_SLUGS[2]),
        phase_spec("二叉树递归思维", "围绕前中后序位置、子树返回值和 BST 性质建立树问题框架。", ["Tree", "Binary Tree", "Binary Search Tree"], LABULADONG_PHASE_SLUGS[3]),
        phase_spec("图论与并查集", "从图遍历推进到拓扑排序、并查集、最短路和最小生成树。", ["Graph", "Union Find", "Shortest Path"], LABULADONG_PHASE_SLUGS[4]),
        phase_spec("动态规划状态设计", "统一练习状态、选择、转移和 base case，并覆盖典型序列与状态机问题。", ["Dynamic Programming", "Memoization"], LABULADONG_PHASE_SLUGS[5]),
        phase_spec("单调结构、堆与设计", "掌握单调栈、单调队列、优先队列和缓存结构中的状态维护。", ["Monotonic Stack", "Heap", "Design"], LABULADONG_PHASE_SLUGS[6]),
        phase_spec("高频综合应用", "用经典综合题检验框架迁移、边界控制和数据结构组合能力。", ["Array", "Linked List", "Design", "Math"], LABULADONG_PHASE_SLUGS[7]),
    ]
    refs: list[dict[str, Any]] = []
    source_order = 0
    for phase_index, phase in enumerate(phase_specs, start=1):
        sort_order = 0
        for slug in phase.pop("sourceThemes"):
            if not problem_index.has_slug(slug):
                raise ValueError(f"missing local problem for labuladong route: {slug}")
            source_order += 1
            sort_order += 1
            refs.append(problem_ref(
                "labuladong_algo_thinking",
                phase_index,
                sort_order,
                source_order,
                slug,
                problem_index.title(slug),
                problem_index.difficulty(slug),
                phase["title"],
                leetcode_url(slug),
                {
                    "sourceKey": "labuladong_reference",
                    "sourceName": LABULADONG_SOURCE_NAME,
                    "sourceChapter": phase["title"],
                    "sourceStrategy": "reference_framework_local_problem_rebuild",
                    "sourceTags": problem_index.tags(slug),
                },
            ))
    template = template_row(
        template_id="labuladong_algo_thinking",
        title="labuladong 核心算法框架训练计划",
        summary="用 8 周跨题型训练双指针、搜索、树图、动态规划和单调结构等核心解题框架。",
        goal="建立先识别问题结构、再套用算法框架并检查边界的稳定解题流程。",
        intent="LONG_TERM_LEARNING",
        level="INTERMEDIATE",
        duration_weeks=8,
        weekly_hours=8,
        difficulty_preference="MIXED",
        target_audience="已经刷过基础题，但知识点分散、希望用框架思维串联不同题型的学习者。",
        prerequisites=["理解常见数据结构", "能独立完成部分 Easy 和 Medium 题"],
        recommended_for=["希望建立跨题型解题框架", "刷题数量不少但迁移能力不足", "需要系统复盘核心算法套路"],
        not_recommended_for=["完全没有数据结构基础", "只想练单一专题", "已经完整掌握同类框架路线"],
        expected_outcome="完成后能从题目特征定位双指针、搜索、树图或动态规划框架，并能解释状态、不变量和关键边界。",
        source_name=LABULADONG_SOURCE_NAME,
        source_url=LABULADONG_SOURCE_URL,
        source_commit=LABULADONG_COMMIT,
        source_data_path="README.md; 算法思维系列; 数据结构系列; 动态规划系列; 高频面试系列",
        source_description="labuladong 固定版本中的核心刷题框架和章节结构。",
        curation_notes="仅参考跨题型框架顺序，由 algo-mentor 从本地题库精选 64 道不重复题重建可执行路线，不复制文章、图示或代码。",
        license_notice="仓库未提供可直接内置内容的 LICENSE；内部模板只参考章节结构并使用本地题库重建。",
        phases=phase_specs,
        metadata={"sourceProblemCount": len(refs), "sourceStrategy": "reference_framework_local_problem_rebuild"},
    )
    return template, refs


def build_sql_50_template(
    problem_index: ProblemIndex,
    detail: dict[str, Any],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    phases = [
        official_phase("基础查询", "掌握 SELECT、过滤、去重和基础条件表达式。", ["Database", "SQL", "Select"], ["Select"]),
        official_phase("基础连接", "训练内连接、外连接和多表关联条件。", ["Database", "SQL", "Join"], ["Basic Joins"]),
        official_phase("聚合函数", "掌握 COUNT、SUM、AVG 和条件聚合。", ["Database", "SQL", "Aggregation"], ["Basic Aggregate Functions"]),
        official_phase("排序与分组", "稳定处理 GROUP BY、HAVING、排序和分组统计。", ["Database", "SQL", "Grouping"], ["Sorting and Grouping"]),
        official_phase("进阶查询与连接", "训练条件连接、窗口前置思维和复杂结果集组合。", ["Database", "SQL", "Advanced Join"], ["Advanced Select and Joins"]),
        official_phase("子查询", "掌握相关子查询、集合判断和分层查询。", ["Database", "SQL", "Subquery"], ["Subqueries"]),
        official_phase("字符串与高级子句", "完成字符串函数、正则和高级过滤的综合训练。", ["Database", "SQL", "String Function"], ["Advanced String Functions / Regex / Clause"]),
    ]
    return build_official_study_plan_template(
        problem_index=problem_index,
        detail=detail,
        template_id="leetcode_sql_50",
        plan_slug="top-sql-50",
        title="LeetCode SQL 50 系统训练计划",
        summary="用 7 周完成 LeetCode SQL 50，从基础查询推进到连接、聚合、子查询和字符串处理。",
        goal="系统掌握数据分析和后端面试中最常见的 SQL 查询模式。",
        duration_weeks=7,
        weekly_hours=6,
        target_audience="希望系统学习 SQL 查询、准备数据或后端岗位面试的学习者。",
        prerequisites=["了解关系型数据库和表的基本概念", "能够阅读简单 SELECT 语句"],
        recommended_for=["准备 SQL 面试题", "需要补齐查询、连接和聚合基础", "希望按官方路线完成 50 题"],
        not_recommended_for=["只需要算法数据结构训练", "需要数据库设计或运维课程", "已经熟练掌握复杂 SQL"],
        expected_outcome="完成后能独立编写常见查询、连接、聚合、子查询和字符串处理 SQL，并复盘典型错误。",
        phases=phases,
        intent="PRACTICE_GOAL",
        level="BEGINNER",
        difficulty_preference="MIXED",
        programming_language="SQL",
    )


def build_javascript_30_days_template(
    problem_index: ProblemIndex,
    detail: dict[str, Any],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    phases = [
        official_phase("闭包与函数作用域", "掌握闭包、词法作用域和函数状态保存。", ["JavaScript", "Closure"], ["Closures"]),
        official_phase("数组与函数变换", "训练 map/filter/reduce 和高阶函数变换。", ["JavaScript", "Array", "Function"], ["Basic Array Transformations", "Function Transformations"]),
        official_phase("Promise 与时间控制", "掌握异步 Promise、延迟、取消和并发时序。", ["JavaScript", "Promise", "Async"], ["Promises and Time"]),
        official_phase("JSON 与对象操作", "训练对象遍历、序列化、组合和深层变换。", ["JavaScript", "JSON", "Object"], ["JSON"]),
        official_phase("类与综合收尾", "完成类、事件和综合挑战，整理 JavaScript 常用实现模式。", ["JavaScript", "Class", "Design"], ["Classes", "Summarize Your 30-day Journey with Bonus Challenges!"]),
    ]
    return build_official_study_plan_template(
        problem_index=problem_index,
        detail=detail,
        template_id="leetcode_javascript_30_days",
        plan_slug="30-days-of-javascript",
        title="LeetCode JavaScript 30 天训练计划",
        summary="用 5 周完成 JavaScript 30 天官方路线，覆盖闭包、高阶函数、Promise、JSON 和类。",
        goal="通过短题训练巩固现代 JavaScript 的函数式、异步和对象建模能力。",
        duration_weeks=5,
        weekly_hours=5,
        target_audience="有基础编程经验，希望系统补齐现代 JavaScript 核心语言能力的学习者。",
        prerequisites=["了解变量、函数、数组和对象", "能运行基础 JavaScript 代码"],
        recommended_for=["前端或 Node.js 初学者", "准备 JavaScript 基础面试", "希望完成结构化 30 天练习"],
        not_recommended_for=["只需要算法数据结构训练", "完全没有编程基础", "需要浏览器框架专项课程"],
        expected_outcome="完成后能熟练使用闭包、高阶函数、Promise、JSON 和类完成常见 JavaScript 编程任务。",
        phases=phases,
        intent="PRACTICE_GOAL",
        level="BEGINNER",
        difficulty_preference="EASY",
        programming_language="JavaScript",
    )


def build_pandas_introduction_template(
    problem_index: ProblemIndex,
    detail: dict[str, Any],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    phases = [
        official_phase("DataFrame 创建与检查", "掌握 DataFrame 创建、形状检查和基础数据预览。", ["Python", "Pandas", "DataFrame"], ["Pandas Data Structures", "Data Inspection"]),
        official_phase("数据选择", "训练行列选择、条件过滤和索引操作。", ["Python", "Pandas", "Selection"], ["Data Selecting"]),
        official_phase("数据清洗", "处理缺失值、重复值、类型转换和列修改。", ["Python", "Pandas", "Data Cleaning"], ["Data Cleaning"]),
        official_phase("表结构变换", "掌握拼接、透视、熔化和链式调用。", ["Python", "Pandas", "Reshaping"], ["Table Reshaping", "Advanced Techniques"]),
    ]
    return build_official_study_plan_template(
        problem_index=problem_index,
        detail=detail,
        template_id="leetcode_pandas_introduction",
        plan_slug="introduction-to-pandas",
        title="LeetCode Pandas 入门训练计划",
        summary="用 4 周完成 Pandas 入门官方路线，覆盖 DataFrame、选择、清洗和表结构变换。",
        goal="建立使用 Pandas 读取、选择、清洗和重塑表格数据的基础能力。",
        duration_weeks=4,
        weekly_hours=4,
        target_audience="掌握 Python 基础，希望开始进行表格数据处理和分析的学习者。",
        prerequisites=["掌握 Python 基础语法", "了解列表、字典和函数"],
        recommended_for=["Pandas 初学者", "需要数据清洗基础", "准备数据分析岗位基础练习"],
        not_recommended_for=["只需要算法数据结构训练", "已经熟练使用 Pandas", "需要机器学习建模课程"],
        expected_outcome="完成后能独立创建、检查、选择、清洗和重塑常见 DataFrame。",
        phases=phases,
        intent="PRACTICE_GOAL",
        level="BEGINNER",
        difficulty_preference="EASY",
        programming_language="Python3",
    )


def build_pandas_30_days_template(
    problem_index: ProblemIndex,
    detail: dict[str, Any],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    review_group = "Strengthen Your Learning by Solving this Question"
    phases = [
        official_phase("过滤与字符串处理", "训练条件过滤、文本匹配和字符串列变换。", ["Python", "Pandas", "Filtering", "String"], ["Data Filtering", "String Methods", review_group]),
        official_phase("数据操作", "完成列变换、排序、去重和组合操作。", ["Python", "Pandas", "Data Manipulation"], ["Data Manipulation"]),
        official_phase("统计分析", "掌握基础统计、排名和分布计算。", ["Python", "Pandas", "Statistics"], ["Statistics", review_group]),
        official_phase("数据聚合", "训练 groupby、聚合函数和分组指标计算。", ["Python", "Pandas", "Aggregation"], ["Data Aggregation"]),
        official_phase("数据集成与综合复盘", "完成多表连接、拼接和整套 Pandas 路线复盘。", ["Python", "Pandas", "Join", "Integration"], ["Data Integration", review_group]),
    ]
    return build_official_study_plan_template(
        problem_index=problem_index,
        detail=detail,
        template_id="leetcode_pandas_30_days",
        plan_slug="30-days-of-pandas",
        title="LeetCode Pandas 30 天进阶计划",
        summary="用 5 周完成 Pandas 30 天官方路线，覆盖过滤、字符串、统计、聚合和数据集成。",
        goal="通过完整题单提升 Pandas 数据清洗、统计聚合和多表处理能力。",
        duration_weeks=5,
        weekly_hours=6,
        target_audience="已经掌握 Pandas 基础，希望通过连续练习提升数据处理熟练度的学习者。",
        prerequisites=["完成 Pandas 基础学习", "能使用 DataFrame 进行选择和清洗"],
        recommended_for=["准备数据分析岗位", "需要强化 Pandas 实战", "希望完成 30 天连续训练"],
        not_recommended_for=["完全没有 Python 基础", "只需要算法数据结构训练", "需要机器学习建模课程"],
        expected_outcome="完成后能熟练完成过滤、字符串处理、统计、聚合和多表集成，并形成 Pandas 常用操作清单。",
        phases=phases,
        intent="LONG_TERM_LEARNING",
        level="INTERMEDIATE",
        difficulty_preference="MIXED",
        programming_language="Python3",
    )


def build_official_study_plan_template(
    *,
    problem_index: ProblemIndex,
    detail: dict[str, Any],
    template_id: str,
    plan_slug: str,
    title: str,
    summary: str,
    goal: str,
    duration_weeks: int,
    weekly_hours: int,
    target_audience: str,
    prerequisites: list[str],
    recommended_for: list[str],
    not_recommended_for: list[str],
    expected_outcome: str,
    phases: list[dict[str, Any]],
    intent: str = "INTERVIEW_SPRINT",
    level: str = "INTERMEDIATE",
    difficulty_preference: str = "MIXED",
    interview_oriented: bool = True,
    programming_language: str = "Java",
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    group_rows = [
        (str(group["name"]), list(group.get("questions") or []))
        for group in detail.get("planSubGroups") or []
    ]
    configured_groups = [name for phase in phases for name in phase["sourceThemes"]]
    source_groups = [name for name, _ in group_rows]
    if Counter(configured_groups) != Counter(source_groups):
        missing = list((Counter(source_groups) - Counter(configured_groups)).elements())
        unknown = list((Counter(configured_groups) - Counter(source_groups)).elements())
        raise ValueError(f"study plan groups changed: {plan_slug}, missing={missing}, unknown={unknown}")
    groups_by_name: dict[str, list[list[dict[str, Any]]]] = {}
    for group_name, questions in group_rows:
        groups_by_name.setdefault(group_name, []).append(questions)
    consumed_groups: Counter[str] = Counter()
    refs: list[dict[str, Any]] = []
    seen: set[str] = set()
    source_order = 0
    for phase_index, phase in enumerate(phases, start=1):
        sort_order = 0
        phase_source_groups = phase.pop("sourceThemes")
        for group_name in phase_source_groups:
            occurrence = consumed_groups[group_name]
            consumed_groups[group_name] += 1
            for question in groups_by_name[group_name][occurrence]:
                slug = normalize_slug(str(question["titleSlug"]))
                if slug in seen:
                    raise ValueError(f"duplicate study plan slug: {plan_slug}#{slug}")
                seen.add(slug)
                source_order += 1
                sort_order += 1
                refs.append(problem_ref(
                    template_id,
                    phase_index,
                    sort_order,
                    source_order,
                    slug,
                    str(question.get("title") or problem_index.title(slug)),
                    str(question.get("difficulty") or problem_index.difficulty(slug)),
                    group_name,
                    leetcode_url(slug),
                    {
                        "paidOnly": bool(question.get("paidOnly", False)),
                        "sourceGroup": group_name,
                        "sourceGroupOccurrence": occurrence + 1,
                        "sourceKey": "leetcode_official_reference",
                        "sourceLicense": "official-reference",
                        "sourceName": LEETCODE_SOURCE_NAME,
                        "sourcePlanSlug": plan_slug,
                        "sourceStrategy": "direct_official_study_plan_metadata",
                        "sourceTags": problem_index.tags(slug),
                        "sourceVersion": LEETCODE_SOURCE_VERSION,
                    },
                ))
    template = template_row(
        template_id=template_id,
        title=title,
        summary=summary,
        goal=goal,
        intent=intent,
        level=level,
        duration_weeks=duration_weeks,
        weekly_hours=weekly_hours,
        difficulty_preference=difficulty_preference,
        target_audience=target_audience,
        prerequisites=prerequisites,
        recommended_for=recommended_for,
        not_recommended_for=not_recommended_for,
        expected_outcome=expected_outcome,
        source_name=LEETCODE_SOURCE_NAME,
        source_url=f"https://leetcode.com/studyplan/{plan_slug}/",
        source_commit=LEETCODE_SOURCE_VERSION,
        source_data_path=f"GraphQL studyPlanV2Detail:{plan_slug}",
        source_description=f"LeetCode 官方 {detail.get('name') or title} Study Plan 的主题分组、题目顺序、标题、slug 和难度元数据。",
        curation_notes="保留官方主题内题目顺序，并把相邻主题合并为可执行阶段；不复制题面、题解或代码。",
        license_notice="LeetCode 官方 Study Plan；内部 seed 仅使用结构化题目元数据和来源链接。",
        phases=phases,
        metadata={
            "sourceGroupCount": len(group_rows),
            "sourceProblemCount": len(refs),
            "sourceStrategy": "direct_official_study_plan_metadata",
        },
        programming_language=programming_language,
    )
    return template, refs


def official_phase(title: str, focus: str, tags: list[str], source_groups: list[str]) -> dict[str, Any]:
    return phase_spec(title, focus, tags, source_groups)


def lcci_order(row: dict[str, Any]) -> tuple[int, int]:
    match = re.search(r"(\d+)\.(\d+)", str(row["frontend_id"]))
    if not match:
        raise ValueError(f"invalid LCCI frontend id: {row['frontend_id']}")
    return int(match.group(1)), int(match.group(2))


def lcci_phase_index(chapter: int, question: int) -> int:
    if chapter == 1:
        return 1
    if chapter in {2, 3}:
        return 2
    if chapter == 4:
        return 3
    if chapter == 5 or (chapter == 8 and question <= 7):
        return 4
    if chapter == 8 or chapter == 10:
        return 5
    if chapter == 16:
        return 6 if question <= 13 else 7
    if chapter == 17:
        if question <= 13:
            return 8
        if question <= 19:
            return 9
        return 10
    raise ValueError(f"unsupported LCCI chapter: {chapter:02d}.{question:02d}")


def build_sean_beginner_template(
    problem_index: ProblemIndex,
    questions_payload: Any,
    roadmaps_text: str,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    question_rows = questions_payload.get("data", []) if isinstance(questions_payload, dict) else questions_payload
    questions = {str(row["slug"]): row for row in question_rows}
    source_phases = parse_sean_beginner_phases(roadmaps_text)
    phase_specs = [
        sean_phase("数组与哈希", "建立遍历、索引、去重和常数时间查找的基础模型。", ["Array", "Hash Table"], ["Arrays & Hash Tables"]),
        sean_phase("双指针", "掌握同向、相向和快慢指针，并结合排序处理数组问题。", ["Two Pointers"], ["Two Pointers"]),
        sean_phase("滑动窗口", "训练固定窗口、可变窗口和窗口收缩条件。", ["Sliding Window", "String"], ["Sliding Window"]),
        sean_phase("链表指针", "熟悉链表遍历、反转、环检测、合并和删除操作。", ["Linked List"], ["Linked Lists"]),
        sean_phase("二分搜索", "建立有序区间、旋转数组和答案边界的二分思维。", ["Binary Search"], ["Binary Search"]),
        sean_phase("树的 DFS 与 BFS", "用递归和队列完成树的遍历、路径与性质判断。", ["Tree", "Depth First Search", "Breadth First Search"], ["Trees - DFS & BFS"]),
        sean_phase("排序与区间", "通过排序预处理掌握区间合并、插入和冲突判断。", ["Sorting", "Intervals"], ["Sorting & Intervals"]),
        sean_phase("图的 BFS 与 DFS", "训练连通块、网格搜索、图建模和课程依赖。", ["Graph", "Breadth First Search", "Depth First Search"], ["Graphs - BFS & DFS"]),
        sean_phase("堆与优先队列", "掌握 Top K、最近点、多路选择和调度问题。", ["Heap", "Priority Queue"], ["Heaps & Priority Queues"]),
        sean_phase("矩阵遍历与前缀和", "完成矩阵模拟并用前缀和建立区间查询基础。", ["Matrix", "Prefix Sum"], ["Matrix Traversal", "Prefix Sums"]),
    ]
    source_phase_by_name = {phase["name"]: phase for phase in source_phases}
    refs: list[dict[str, Any]] = []
    source_order = 0
    for phase_index, spec in enumerate(phase_specs, start=1):
        sort_order = 0
        source_names = spec.pop("sourceThemes")
        for source_name in source_names:
            for slug in source_phase_by_name[source_name]["slugs"]:
                row = questions.get(slug, {})
                source_order += 1
                sort_order += 1
                refs.append(problem_ref(
                    "leetcode_patterns_beginner_roadmap",
                    phase_index,
                    sort_order,
                    source_order,
                    slug,
                    str(row.get("title") or problem_index.title(slug)),
                    str(row.get("difficulty") or problem_index.difficulty(slug)),
                    spec["title"],
                    leetcode_url(slug),
                    {
                        "premium": bool(row.get("premium", False)),
                        "sourceKey": "sean_patterns",
                        "sourceLicense": "CC-BY-NC-4.0",
                        "sourceName": SEAN_SOURCE_NAME,
                        "sourcePatterns": list(row.get("pattern") or []),
                        "sourceRoadmapPhase": source_name,
                        "sourceStrategy": "direct_beginner_roadmap_metadata",
                    },
                ))
    template = template_row(
        template_id="leetcode_patterns_beginner_roadmap",
        title="算法模式入门训练计划",
        summary="用 10 周按常见算法模式建立刷题基础，从数组哈希逐步推进到图、堆、矩阵和前缀和。",
        goal="通过稳定的题型顺序建立算法面试入门者的模式识别和基础实现能力。",
        intent="LONG_TERM_LEARNING",
        level="BEGINNER",
        duration_weeks=10,
        weekly_hours=6,
        difficulty_preference="MIXED",
        target_audience="具备基础语法，但尚未形成数据结构与算法题型地图的入门学习者。",
        prerequisites=["能使用一种语言编写函数、循环和基础集合操作", "愿意按周完成同类题复盘"],
        recommended_for=["第一次系统准备算法面试", "刷题顺序混乱", "希望从 Easy 逐步过渡到 Medium"],
        not_recommended_for=["已经完成系统面试 150 题路线", "只想集中攻克 Hard 级专项"],
        expected_outcome="完成后能识别十类常见算法模式，独立完成基础题并为后续系统面试路线打好基础。",
        source_name=SEAN_SOURCE_NAME,
        source_url=SEAN_SOURCE_URL,
        source_commit=SEAN_COMMIT,
        source_data_path="src/data/questions.json; src/data/roadmaps.ts#beginnerRoadmap",
        source_description="leetcode-patterns 的 Beginner Roadmap 阶段顺序及 questions.json 结构化题目元数据。",
        curation_notes="保留 Beginner Roadmap 顺序，将矩阵遍历与前缀和合并为第 10 周；阶段目标和复盘标准由 algo-mentor 自写。",
        license_notice="CC-BY-NC-4.0；内部 seed 仅使用 roadmap 顺序和题目元数据，不复制来源说明或推荐 note。",
        phases=phase_specs,
        metadata={"sourceProblemCount": len(refs), "sourceStrategy": "direct_beginner_roadmap_metadata"},
    )
    return template, refs


def parse_sean_beginner_phases(text: str) -> list[dict[str, Any]]:
    start = text.index("export const beginnerRoadmap")
    end = text.index("export const experiencedRoadmap", start)
    block = text[start:end]
    phase_matches = list(re.finditer(r'title:\s*"Phase\s+\d+:\s*([^"]+)"', block))
    phases: list[dict[str, Any]] = []
    for index, match in enumerate(phase_matches):
        next_start = phase_matches[index + 1].start() if index + 1 < len(phase_matches) else len(block)
        phase_block = block[match.start():next_start]
        phases.append({
            "name": match.group(1).strip(),
            "slugs": list(dict.fromkeys(re.findall(r'slug:\s*"([^"]+)"', phase_block))),
        })
    return phases


def phase_spec(title: str, focus: str, tags: list[str], source_themes: Any) -> dict[str, Any]:
    return {
        "phaseIndex": 0,
        "title": title,
        "durationWeeks": 1,
        "focus": focus,
        "tags": tags,
        "sourceThemes": source_themes,
    }


def sean_phase(title: str, focus: str, tags: list[str], source_names: list[str]) -> dict[str, Any]:
    return phase_spec(title, focus, tags, source_names)


def template_row(
    *,
    template_id: str,
    title: str,
    summary: str,
    goal: str,
    intent: str,
    level: str,
    duration_weeks: int,
    weekly_hours: int,
    difficulty_preference: str,
    target_audience: str,
    prerequisites: list[str],
    recommended_for: list[str],
    not_recommended_for: list[str],
    expected_outcome: str,
    source_name: str,
    source_url: str,
    source_commit: str,
    source_data_path: str,
    source_description: str,
    curation_notes: str,
    license_notice: str,
    phases: list[dict[str, Any]],
    metadata: dict[str, Any],
    programming_language: str = "Java",
) -> dict[str, Any]:
    for index, phase in enumerate(phases, start=1):
        phase["phaseIndex"] = index
    topic_preferences = list(dict.fromkeys(tag for phase in phases for tag in phase.pop("tags", [])))
    return {
        "templateId": template_id,
        "title": title,
        "summary": summary,
        "goal": goal,
        "intent": intent,
        "level": level,
        "defaultDurationWeeks": duration_weeks,
        "defaultWeeklyHours": weekly_hours,
        "programmingLanguage": programming_language,
        "difficultyPreference": difficulty_preference,
        "topicPreferences": topic_preferences,
        "targetAudience": target_audience,
        "prerequisites": prerequisites,
        "recommendedFor": recommended_for,
        "notRecommendedFor": not_recommended_for,
        "expectedOutcome": expected_outcome,
        "sourceName": source_name,
        "sourceUrl": source_url,
        "sourceCommit": source_commit,
        "sourceDataPath": source_data_path,
        "sourceDescription": source_description,
        "curationNotes": curation_notes,
        "licenseNotice": license_notice,
        "metadata": metadata,
        "phases": phases,
    }


def problem_ref(
    template_id: str,
    phase_index: int,
    sort_order: int,
    source_order: int,
    slug: str,
    source_title: str,
    source_difficulty: str,
    pattern: str,
    source_url: str,
    metadata: dict[str, Any],
) -> dict[str, Any]:
    return {
        "templateId": template_id,
        "phaseIndex": phase_index,
        "sortOrder": sort_order,
        "sourceOrder": source_order,
        "problemSlug": normalize_slug(slug),
        "sourceTitle": source_title,
        "sourceDifficulty": normalize_difficulty(source_difficulty),
        "pattern": pattern,
        "sourceUrl": source_url,
        "metadata": metadata,
    }


def write_template_sources(
    output_root: Path,
    outputs: dict[str, tuple[dict[str, Any], list[dict[str, Any]]]],
) -> None:
    for template_id in TEMPLATE_IDS:
        template, refs = outputs[template_id]
        template_dir = output_root / template_id
        write_json(template_dir / "template.json", template)
        write_jsonl(template_dir / "problem_refs.jsonl", refs)


def validate_outputs(
    outputs: dict[str, tuple[dict[str, Any], list[dict[str, Any]]]],
) -> None:
    if list(outputs) != TEMPLATE_IDS:
        raise ValueError("P1-B template output order changed unexpectedly")
    for template_id, (template, refs) in outputs.items():
        phases = template["phases"]
        if sum(int(phase["durationWeeks"]) for phase in phases) != int(template["defaultDurationWeeks"]):
            raise ValueError(f"phase duration mismatch: {template_id}")
        phase_indexes = {int(phase["phaseIndex"]) for phase in phases}
        seen: set[str] = set()
        source_orders: list[int] = []
        sort_orders_by_phase: dict[int, list[int]] = {}
        for ref in refs:
            phase_index = int(ref["phaseIndex"])
            if phase_index not in phase_indexes:
                raise ValueError(f"unknown phase: {template_id}#{phase_index}")
            slug = str(ref["problemSlug"])
            if slug in seen:
                raise ValueError(f"duplicate slug: {template_id}#{slug}")
            seen.add(slug)
            source_orders.append(int(ref["sourceOrder"]))
            sort_orders_by_phase.setdefault(phase_index, []).append(int(ref["sortOrder"]))
        if source_orders != list(range(1, len(refs) + 1)):
            raise ValueError(f"sourceOrder must be contiguous: {template_id}")
        for phase_index, sort_orders in sort_orders_by_phase.items():
            if sort_orders != list(range(1, len(sort_orders) + 1)):
                raise ValueError(f"sortOrder must be contiguous: {template_id}#{phase_index}")


def sword_offer_order(row: dict[str, Any]) -> tuple[int, int]:
    frontend_id = str(row["frontend_id"])
    number = sword_offer_number(frontend_id)
    suffix = frontend_id[re.search(r"\d+", frontend_id).end():].strip().replace("-", "").strip()
    suffix_order = {"": 0, "I": 1, "II": 2, "III": 3}.get(suffix, 9)
    return number, suffix_order


def sword_offer_number(frontend_id: str) -> int:
    match = re.search(r"\d+", frontend_id)
    if not match:
        raise ValueError(f"invalid Sword Offer frontend id: {frontend_id}")
    return int(match.group())


def slug_from_url(url: str) -> str:
    match = re.search(r"/problems/([^/]+)/?", url)
    if not match:
        raise ValueError(f"missing problem slug in URL: {url}")
    return normalize_slug(match.group(1))


def leetcode_url(slug: str) -> str:
    return f"https://leetcode.com/problems/{slug}/"


def normalize_slug(value: str) -> str:
    return value.strip().strip("/").lower()


def normalize_display_id(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip()).lower()


def normalize_difficulty(value: str) -> str:
    return {"easy": "Easy", "medium": "Medium", "hard": "Hard"}.get(value.strip().lower(), "Medium")


def title_from_slug(slug: str) -> str:
    return " ".join(part.capitalize() for part in slug.split("-"))


def clean_markdown(value: str) -> str:
    return re.sub(r"[*_`]", "", value).strip()


if __name__ == "__main__":
    main()
