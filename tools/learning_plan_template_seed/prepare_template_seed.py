#!/usr/bin/env python3
"""生成学习计划模板 seed。"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import urllib.request
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

if __package__ in (None, ""):
    sys.path.append(str(Path(__file__).resolve().parents[2]))

from tools.problem_seed.leetcode_api import read_jsonl, write_json, write_jsonl


DEFAULT_OUTPUT_DIR = Path("data/learning-plan-template-seed")
DEFAULT_TEMPLATE_SOURCE_DIR = Path("data/learning-plan-template-sources/templates")
DEFAULT_TEMPLATE_ORDER_PATH = Path("data/learning-plan-template-sources/template_order.json")
DEFAULT_LOCAL_PROBLEMS_PATH = Path("data/seed/problems.jsonl")
DEFAULT_GENERATED_AT = "2026-07-09T00:00:00+00:00"

TEMPLATES_FILE = "learning_plan_templates.jsonl"
PROBLEM_REFS_FILE = "learning_plan_template_problem_refs.jsonl"
MANIFEST_FILE = "learning_plan_template_seed_manifest.json"
METADATA_FILE = "learning_plan_template_seed_metadata.md"
TEMPLATE_SOURCE_TEMPLATE_FILE = "template.json"
TEMPLATE_SOURCE_PROBLEM_REFS_FILE = "problem_refs.jsonl"
DERIVED_TEMPLATE_METADATA_KEYS = {
    "matchedProblemCount",
    "missingProblemCount",
    "missingProblems",
    "sourceTags",
}

NEETCODE_SOURCE = {
    "key": "neetcode",
    "name": "neetcode-gh/leetcode",
    "url": "https://github.com/neetcode-gh/leetcode",
    "commit": "9907b7fed441fa55083c0751e208b7197101dbba",
    "dataPath": ".problemSiteData.json",
    "licenseNotice": "MIT License；本 seed 只使用题名、slug、difficulty、pattern、题单标记和来源链接，不包含题解源码或文章内容。",
}
TIH_SOURCE = {
    "key": "tih",
    "name": "yangshun/tech-interview-handbook",
    "url": "https://github.com/yangshun/tech-interview-handbook",
    "commit": "8ee2acb54a05c4add123a824d15e7dfc4e703b2f",
    "dataPath": "apps/website/contents/best-practice-questions.md",
    "licenseNotice": "MIT License；本 seed 只解析题名、difficulty、LeetCode URL、周次和 optional/premium 标记，不复制文章正文。",
}
HALFROST_SOURCE = {
    "key": "halfrost",
    "name": "halfrost/LeetCode-Go",
    "url": "https://github.com/halfrost/LeetCode-Go",
    "commit": "3bcc916680298295e06060ca9790304c1f1b78b6",
    "dataPath": "ctl/meta/*",
    "licenseNotice": "MIT License；本 seed 只使用题号、题名、difficulty、标签来源和顺序，不包含题解代码。",
}
LEETCODE_MASTER_SOURCE = {
    "key": "leetcode_master_reference",
    "name": "youngyangyang04/leetcode-master",
    "url": "https://github.com/youngyangyang04/leetcode-master",
    "commit": "86f78fde8cb62d10c3b5e38b7e6b6e0705850f92",
    "dataPath": "README.md",
    "licenseNotice": "未发现仓库 LICENSE；本 seed 只作学习顺序参考，阶段说明、题目组合和复盘建议由 algo-mentor 重建。",
}
LABULADONG_SOURCE = {
    "key": "labuladong_reference",
    "name": "labuladong/fucking-algorithm",
    "url": "https://github.com/labuladong/fucking-algorithm",
    "commit": "b1f23cb9605f6146ff78bafad71e795176439b99",
    "dataPath": "README.md; labuladong.online/algo/",
    "licenseNotice": "未发现可用于直接内置的 LICENSE；本 seed 只记录为专题结构参考，不复制文章、图示或代码。",
}
LEETCODE_OFFICIAL_SOURCE = {
    "key": "leetcode_official_reference",
    "name": "LeetCode official Study Plan",
    "url": "https://leetcode.com/studyplan/",
    "commit": "accessed-2026-07-06",
    "dataPath": "top-100-liked; programming-skills; dynamic-programming; graph-theory; binary-search",
    "licenseNotice": "官方网页仅作外链和目标参考；本 seed 不复制题面、题解、付费内容或官方题单全文。",
}
LOCAL_SOURCE = {
    "key": "algo_mentor_local_problem_seed",
    "name": "algo-mentor local problem seed",
    "url": "data/seed/problems.jsonl",
    "commit": "local-2026-07-06",
    "dataPath": "data/seed/problems.jsonl",
    "licenseNotice": "使用项目本地题库中的 slug、frontendId、标题、difficulty 和 tagValues 组织内部模板。",
}
ROOT_SOURCE = {
    "name": "algo-mentor learning-plan-template-sources",
    "url": "data/learning-plan-template-sources",
    "commit": "template-source-split-2026-07-09",
    "dataPath": "data/learning-plan-template-sources/templates/*/{template.json,problem_refs.jsonl}",
}
SOURCE_DEFINITIONS = [
    NEETCODE_SOURCE,
    TIH_SOURCE,
    HALFROST_SOURCE,
    LEETCODE_MASTER_SOURCE,
    LABULADONG_SOURCE,
    LEETCODE_OFFICIAL_SOURCE,
    LOCAL_SOURCE,
]

NEETCODE_RAW_URL = (
    "https://raw.githubusercontent.com/neetcode-gh/leetcode/"
    f"{NEETCODE_SOURCE['commit']}/{NEETCODE_SOURCE['dataPath']}"
)
TIH_RAW_URL = (
    "https://raw.githubusercontent.com/yangshun/tech-interview-handbook/"
    f"{TIH_SOURCE['commit']}/{TIH_SOURCE['dataPath']}"
)
HALFROST_RAW_BASE = (
    "https://raw.githubusercontent.com/halfrost/LeetCode-Go/"
    f"{HALFROST_SOURCE['commit']}/ctl/meta"
)
HALFROST_META_FILES = [
    "Backtracking",
    "Binary_Search",
    "Bit_Manipulation",
    "Breadth_First_Search",
    "Depth_First_Search",
    "Dynamic_Programming",
    "Sorting",
    "Sliding_Window",
    "Stack",
    "Tree",
    "Two_Pointers",
    "Union_Find",
]

NEETCODE_PHASES = {
    "neetcode_blind_75_interview_core": [
        {
            "title": "数组、哈希与线性结构",
            "durationWeeks": 1,
            "patterns": ["Arrays & Hashing", "Two Pointers", "Sliding Window", "Stack", "Linked List"],
            "focus": "完成数组、哈希、双指针、滑窗、栈和链表的完整 Blind 75 训练。",
        },
        {
            "title": "二分、区间与树",
            "durationWeeks": 1,
            "patterns": ["Binary Search", "Intervals", "Trees", "Tries"],
            "focus": "完成二分、区间、树和 Trie 的完整 Blind 75 训练。",
        },
        {
            "title": "图、回溯与堆",
            "durationWeeks": 1,
            "patterns": ["Graphs", "Advanced Graphs", "Backtracking", "Heap / Priority Queue"],
            "focus": "完成图搜索、回溯和堆相关的完整 Blind 75 训练。",
        },
        {
            "title": "动态规划、贪心与综合收尾",
            "durationWeeks": 1,
            "patterns": ["1-D Dynamic Programming", "2-D Dynamic Programming", "Greedy", "Bit Manipulation", "Math & Geometry"],
            "focus": "完成动态规划、贪心、位运算和数学几何的完整 Blind 75 训练。",
        },
    ],
    "neetcode_150_systematic_interview": [
        {"title": "数组与哈希", "durationWeeks": 1, "patterns": ["Arrays & Hashing"], "focus": "完成 NeetCode 150 中数组与哈希题目，建立频次、去重和分组模型。"},
        {"title": "双指针与滑动窗口", "durationWeeks": 1, "patterns": ["Two Pointers", "Sliding Window"], "focus": "完成双指针和滑窗题目，训练窗口维护、左右边界和收缩条件。"},
        {"title": "栈与二分搜索", "durationWeeks": 1, "patterns": ["Stack", "Binary Search"], "focus": "完成栈和二分题目，训练单调结构、括号状态和边界搜索。"},
        {"title": "链表与区间", "durationWeeks": 1, "patterns": ["Linked List", "Intervals"], "focus": "完成链表和区间题目，训练指针改写、合并、插入和调度边界。"},
        {"title": "树与递归遍历", "durationWeeks": 1, "patterns": ["Trees"], "focus": "完成树题目，训练 DFS/BFS、递归返回值、序列化和二叉搜索树性质。"},
        {"title": "Trie 与堆", "durationWeeks": 1, "patterns": ["Tries", "Heap / Priority Queue"], "focus": "完成 Trie 和堆题目，训练前缀树建模、Top K 和优先级调度。"},
        {"title": "回溯", "durationWeeks": 1, "patterns": ["Backtracking"], "focus": "完成回溯题目，训练选择树、剪枝和状态恢复。"},
        {"title": "图搜索", "durationWeeks": 1, "patterns": ["Graphs"], "focus": "完成图搜索题目，训练连通块、拓扑关系、BFS/DFS 和网格建模。"},
        {"title": "进阶图与贪心", "durationWeeks": 1, "patterns": ["Advanced Graphs", "Greedy"], "focus": "完成进阶图和贪心题目，训练最短路、并查集和局部最优证明。"},
        {"title": "一维动态规划", "durationWeeks": 1, "patterns": ["1-D Dynamic Programming"], "focus": "完成一维动态规划题目，训练状态定义、转移顺序和滚动优化。"},
        {"title": "二维动态规划", "durationWeeks": 1, "patterns": ["2-D Dynamic Programming"], "focus": "完成二维动态规划题目，训练网格、区间和多维状态设计。"},
        {"title": "位运算、数学与综合收尾", "durationWeeks": 1, "patterns": ["Bit Manipulation", "Math & Geometry"], "focus": "完成位运算、数学几何和综合收尾题目，整理完整路线复盘材料。"},
    ],
}

TEMPLATE_CONFIGS = {
    "neetcode_blind_75_interview_core": {
        "sourceFlag": "blind75",
        "title": "NeetCode Blind 75 面试核心计划",
        "summary": "用 4 周跑完面试最核心的 75 道题路线，优先覆盖高频数据结构、图搜索和动态规划基础。",
        "goal": "用 Blind 75 题单建立算法面试核心题型覆盖。",
        "intent": "INTERVIEW_SPRINT",
        "level": "INTERMEDIATE",
        "defaultDurationWeeks": 4,
        "defaultWeeklyHours": 8,
        "difficultyPreference": "MEDIUM",
        "interviewOriented": True,
        "targetAudience": "已有基本编程能力、准备 1 个月左右算法面试冲刺的学习者。",
        "prerequisites": ["能读写一种主力语言", "理解数组、哈希表、链表、树的基本概念"],
        "recommendedFor": ["面试时间较近，需要高频核心题路线", "刷题经验有限但希望快速建立题型地图"],
        "notRecommendedFor": ["完全没有数据结构基础", "希望系统覆盖 150 题以上完整路线的学习者"],
        "expectedOutcome": "完成后能识别主流面试题型，并能独立复盘高频核心题的解法模板和边界条件。",
        "sourceDescription": "NeetCode 公开仓库的 .problemSiteData.json，包含题名、difficulty、pattern、Blind 75 / NeetCode 150 标记和题目链接。",
        "curationNotes": "按 NeetCode Blind 75 标记抽取题目，按 pattern 聚合为 4 个完整执行阶段；生成草稿默认保留所有本地匹配题。",
        "source": NEETCODE_SOURCE,
    },
    "neetcode_150_systematic_interview": {
        "sourceFlag": "neetcode150",
        "title": "NeetCode 150 系统面试计划",
        "summary": "用 12 周覆盖 NeetCode 150 的主流算法面试模式，适合中期系统备战。",
        "goal": "用 NeetCode 150 系统覆盖主流算法面试题型。",
        "intent": "INTERVIEW_SPRINT",
        "level": "INTERMEDIATE",
        "defaultDurationWeeks": 12,
        "defaultWeeklyHours": 10,
        "difficultyPreference": "MEDIUM",
        "interviewOriented": True,
        "targetAudience": "有 2 到 3 个月准备周期、希望系统覆盖面试算法题型的学习者。",
        "prerequisites": ["掌握一门主力语言的基础语法", "了解常见数据结构和时间复杂度", "每周能稳定投入 8 小时以上"],
        "recommendedFor": ["准备中大型技术面试", "希望按题型模式系统刷题", "刷过少量题但缺少完整路线"],
        "notRecommendedFor": ["只剩 1 到 2 周面试准备时间", "只想做单一薄弱专题突破"],
        "expectedOutcome": "完成后能按题型建立系统化解题策略，并形成覆盖数组、树、图、动态规划和综合题的面试复盘材料。",
        "sourceDescription": "NeetCode 公开仓库的 .problemSiteData.json，包含题名、difficulty、pattern、Blind 75 / NeetCode 150 标记和题目链接。",
        "curationNotes": "按 NeetCode 150 标记抽取题目，沿用 pattern 顺序组织为 12 个完整执行阶段；生成草稿默认保留所有本地匹配题。",
        "source": NEETCODE_SOURCE,
    },
    "tih_best_practice_50_5weeks": {
        "title": "5 周面试冲刺计划",
        "summary": "用 5 周完成 Tech Interview Handbook Best Practice 核心路线，按序列、数据结构、树图、进阶结构和 DP 分周推进。",
        "goal": "在面试倒计时中完成一轮高价值核心题冲刺。",
        "intent": "INTERVIEW_SPRINT",
        "level": "INTERMEDIATE",
        "defaultDurationWeeks": 5,
        "defaultWeeklyHours": 8,
        "difficultyPreference": "MIXED",
        "interviewOriented": True,
        "targetAudience": "已有基础题经验、希望用 5 周做面试前高密度复习的学习者。",
        "prerequisites": ["能完成 Easy/Medium 基础题", "了解数组、链表、树、图和动态规划的常见术语"],
        "recommendedFor": ["面试倒计时 4 到 6 周", "需要明确每周题单和复盘节奏"],
        "notRecommendedFor": ["刚开始学习编程的新手", "需要 12 周以上系统补基础的学习者"],
        "expectedOutcome": "完成后能按周复盘核心题型，并整理出可面试表达的解法模板、复杂度和错题清单。",
        "sourceDescription": "Tech Interview Handbook Best Practice 50 的 5 周 Markdown 题单；Grind 75 只作为外链和结构参考。",
        "curationNotes": "按原 5 周节奏解析题目元数据，optional 和 premium 题保留在 refs metadata 中；阶段目标和复盘建议由 algo-mentor 自写。",
        "source": TIH_SOURCE,
    },
}


def phase_spec(
    title: str,
    duration_weeks: int,
    focus: str,
    tags: list[str],
    slugs: list[str],
) -> dict[str, Any]:
    return {
        "title": title,
        "durationWeeks": duration_weeks,
        "focus": focus,
        "objectives": [
            "完成本阶段推荐题并记录每题的核心建模方式",
            "整理本阶段至少 2 条高频错因和复盘策略",
        ],
        "recommendedTags": tags,
        "acceptanceCriteria": [
            "能独立复述本阶段每类题的适用条件",
            "能写出错题的边界条件、复杂度和可迁移模板",
        ],
        "reviewAdvice": "先按题型归类错题，再用同类题检查模板是否真正掌握。",
        "problemSlugs": slugs,
    }


# Legacy fallback for runs without data/learning-plan-template-sources/.
# New template edits should be made in per-template source directories.
MANUAL_TEMPLATES = {
    "cn_algorithm_foundation_12weeks": {
        "title": "中文系统刷题入门计划",
        "summary": "面向中文初学者的 12 周系统路线，按数组、链表、哈希、字符串、树、回溯、贪心、动态规划和图论逐步推进。",
        "goal": "用中文学习习惯友好的顺序建立算法刷题基础。",
        "intent": "LONG_TERM_LEARNING",
        "level": "BEGINNER",
        "defaultDurationWeeks": 12,
        "defaultWeeklyHours": 6,
        "difficultyPreference": "MIXED",
        "interviewOriented": False,
        "targetAudience": "刚开始系统刷题、偏好中文路线、希望逐步建立知识脉络的学习者。",
        "prerequisites": ["能使用一种语言写基础循环和函数", "愿意按周复盘错题和模板"],
        "recommendedFor": ["刷题路线不清楚的新手", "需要中文语境下的系统推进节奏", "希望先用 Easy/Medium 打基础"],
        "notRecommendedFor": ["已经完成 150 题以上并只想专项突破", "两周内必须面试冲刺的学习者"],
        "expectedOutcome": "完成后能理解常见数据结构和算法题型的学习顺序，并沉淀一套可继续扩展的错题复盘框架。",
        "source": LEETCODE_MASTER_SOURCE,
        "sourceDescription": "以代码随想录 README 的知识脉络为参考，并结合 LeetCode Programming Skills 和本地题库重建执行路线。",
        "curationNotes": "不复制代码随想录文章、图示、题解或 README 原文；阶段目标、验收条件、复盘建议和题目组合均由 algo-mentor 自写。",
        "metadata": {
            "sourceStrategy": "reference_after_rebuild",
            "secondarySources": [LEETCODE_OFFICIAL_SOURCE["name"], LOCAL_SOURCE["name"]],
        },
        "phases": [
            phase_spec("数组与矩阵基础", 1, "完成数组下标、双指针前置和矩阵遍历基础。", ["Array", "Matrix"], [
                "binary-search", "remove-element", "squares-of-a-sorted-array", "minimum-size-subarray-sum", "spiral-matrix",
            ]),
            phase_spec("链表指针操作", 1, "训练链表节点改写、快慢指针和删除边界。", ["Linked List", "Two Pointers"], [
                "reverse-linked-list", "swap-nodes-in-pairs", "remove-nth-node-from-end-of-list", "linked-list-cycle-ii",
            ]),
            phase_spec("哈希与集合建模", 1, "用哈希表处理计数、去重、集合交集和两数关系。", ["Hash Table"], [
                "valid-anagram", "intersection-of-two-arrays", "happy-number", "two-sum", "4sum-ii", "ransom-note",
            ]),
            phase_spec("字符串、栈与队列", 1, "补齐字符串处理、括号匹配和基础队列/栈实现。", ["String", "Stack", "Queue"], [
                "reverse-string", "reverse-words-in-a-string", "valid-parentheses", "implement-queue-using-stacks", "implement-stack-using-queues",
            ]),
            phase_spec("二叉树遍历与性质", 2, "系统练习 DFS/BFS 遍历、树高、路径和 BST 校验。", ["Tree", "Binary Tree"], [
                "binary-tree-preorder-traversal", "binary-tree-level-order-traversal", "invert-binary-tree", "symmetric-tree",
                "maximum-depth-of-binary-tree", "validate-binary-search-tree", "binary-tree-paths", "path-sum",
                "construct-binary-tree-from-inorder-and-postorder-traversal",
            ]),
            phase_spec("回溯搜索", 1, "训练选择列表、撤销选择、剪枝和网格搜索。", ["Backtracking"], [
                "combination-sum", "subsets", "permutations", "word-search",
            ]),
            phase_spec("贪心与区间直觉", 1, "通过局部最优和区间边界建立贪心证明意识。", ["Greedy", "Intervals"], [
                "partition-labels", "jump-game", "gas-station", "candy",
            ]),
            phase_spec("动态规划入门", 2, "从一维状态到背包和字符串 DP，强调状态定义和转移顺序。", ["Dynamic Programming"], [
                "climbing-stairs", "coin-change", "longest-increasing-subsequence", "word-break", "maximum-subarray",
            ]),
            phase_spec("图论基础收尾", 2, "完成网格 BFS/DFS、拓扑排序和连通性基础。", ["Graph", "Breadth-First Search", "Depth-First Search"], [
                "number-of-islands", "rotting-oranges", "course-schedule", "find-if-path-exists-in-graph",
            ]),
        ],
    },
    "topic_dynamic_programming_foundation": {
        "title": "动态规划专项突破计划",
        "summary": "4 周集中突破 DP，按一维状态、背包与路径、子序列、状态机和区间/回文问题递进。",
        "goal": "建立动态规划状态定义、转移方程和复盘表达能力。",
        "intent": "TOPIC_BREAKTHROUGH",
        "level": "INTERMEDIATE",
        "defaultDurationWeeks": 4,
        "defaultWeeklyHours": 8,
        "difficultyPreference": "MEDIUM",
        "interviewOriented": True,
        "targetAudience": "已刷过基础题但动态规划题正确率不稳定的学习者。",
        "prerequisites": ["理解递归和数组遍历", "能写出基础复杂度分析", "做过至少 30 道 Easy/Medium 题"],
        "recommendedFor": ["状态定义不稳定", "背包、子序列或股票题容易混淆", "需要面试前专项补强 DP"],
        "notRecommendedFor": ["完全没有数组和递归基础", "只想学习图论或树专题"],
        "expectedOutcome": "完成后能按题型说明状态、选择、转移顺序和初始化，并形成 DP 错题分类表。",
        "source": HALFROST_SOURCE,
        "sourceDescription": "以 halfrost Dynamic_Programming 元数据中的题号、难度和标签为主，LeetCode 官方 DP 与 labuladong DP 只作阶段结构参考。",
        "curationNotes": "直接内置 halfrost MIT 题单元数据中可匹配的题号/标签；阶段说明和推荐理由由 algo-mentor 自写。",
        "metadata": {
            "sourceStrategy": "mixed_direct_metadata_and_reference_rebuild",
            "secondarySources": [LEETCODE_OFFICIAL_SOURCE["name"], LABULADONG_SOURCE["name"], LOCAL_SOURCE["name"]],
        },
        "phases": [
            phase_spec("一维状态与滚动优化", 1, "用线性序列题训练 dp[i] 含义、初始化和滚动数组。", ["Dynamic Programming"], [
                "climbing-stairs", "min-cost-climbing-stairs", "house-robber", "house-robber-ii", "maximum-subarray", "maximum-product-subarray",
            ]),
            phase_spec("路径、背包与选择", 1, "通过网格路径和硬币/子集问题训练选择集合和容量维度。", ["Dynamic Programming", "Knapsack"], [
                "unique-paths", "unique-paths-ii", "minimum-path-sum", "coin-change", "partition-equal-subset-sum", "target-sum",
            ]),
            phase_spec("子序列与字符串 DP", 1, "集中处理索引双维、匹配关系和字符串切分。", ["Dynamic Programming", "String"], [
                "longest-increasing-subsequence", "longest-common-subsequence", "word-break", "decode-ways", "edit-distance",
            ]),
            phase_spec("状态机、区间与回文", 1, "训练持有/不持有状态、交易约束和回文区间扩展。", ["Dynamic Programming", "State Machine"], [
                "best-time-to-buy-and-sell-stock", "best-time-to-buy-and-sell-stock-ii",
                "best-time-to-buy-and-sell-stock-with-cooldown",
                "best-time-to-buy-and-sell-stock-with-transaction-fee",
                "palindromic-substrings", "longest-palindromic-substring",
            ]),
        ],
    },
    "topic_graph_bfs_dfs": {
        "title": "图论专项突破计划",
        "summary": "4 周覆盖 DFS/BFS、岛屿网格、拓扑排序、并查集和最短路基础，适合补齐图论薄弱项。",
        "goal": "建立图搜索建模、遍历状态和连通性判断能力。",
        "intent": "TOPIC_BREAKTHROUGH",
        "level": "INTERMEDIATE",
        "defaultDurationWeeks": 4,
        "defaultWeeklyHours": 7,
        "difficultyPreference": "MEDIUM",
        "interviewOriented": True,
        "targetAudience": "对图搜索、拓扑关系或并查集不稳定的中级学习者。",
        "prerequisites": ["理解队列、栈和递归", "能读写邻接表或网格遍历", "完成过树的 DFS/BFS 基础题"],
        "recommendedFor": ["岛屿题、课程表、并查集常出错", "需要按图论专题集中复盘"],
        "notRecommendedFor": ["尚未掌握基础树遍历", "只想练一维数组技巧"],
        "expectedOutcome": "完成后能区分 DFS/BFS/拓扑/并查集适用场景，并能写出遍历状态和 visited 规则。",
        "source": HALFROST_SOURCE,
        "sourceDescription": "以 halfrost Breadth_First_Search、Depth_First_Search、Union_Find 元数据为主，LeetCode Graph Theory 和代码随想录图论作结构参考。",
        "curationNotes": "直接内置 halfrost MIT 题号/标签；premium 或本地未收录题只进入缺失审计，不进入生成草稿推荐题。",
        "metadata": {
            "sourceStrategy": "mixed_direct_metadata_and_reference_rebuild",
            "secondarySources": [LEETCODE_OFFICIAL_SOURCE["name"], LEETCODE_MASTER_SOURCE["name"], LOCAL_SOURCE["name"]],
        },
        "phases": [
            phase_spec("DFS/BFS 遍历基础", 1, "完成图和网格的基础遍历，明确 visited、边界和层序处理。", ["Graph", "Depth-First Search", "Breadth-First Search"], [
                "number-of-islands", "max-area-of-island", "clone-graph", "pacific-atlantic-water-flow", "rotting-oranges", "01-matrix",
            ]),
            phase_spec("岛屿与区域变体", 1, "训练网格连通块、边界感染和区域归并。", ["Matrix", "Graph"], [
                "surrounded-regions", "number-of-islands", "max-area-of-island", "pacific-atlantic-water-flow",
            ]),
            phase_spec("拓扑排序与依赖关系", 1, "通过课程表和字典序题训练入度、队列和依赖图。", ["Topological Sort", "Graph"], [
                "course-schedule", "course-schedule-ii", "alien-dictionary",
            ]),
            phase_spec("并查集与加权图收尾", 1, "用连通性、冗余边和最短路题整理图论工具箱。", ["Union Find", "Shortest Path"], [
                "number-of-provinces", "redundant-connection", "accounts-merge", "evaluate-division", "network-delay-time",
                "cheapest-flights-within-k-stops", "min-cost-to-connect-all-points",
            ]),
        ],
    },
    "topic_binary_search_boundaries": {
        "title": "二分与边界专项计划",
        "summary": "2 周集中训练基础二分、旋转数组、左右边界、答案二分和矩阵变体。",
        "goal": "掌握二分搜索模板、边界收缩和答案空间建模。",
        "intent": "TOPIC_BREAKTHROUGH",
        "level": "INTERMEDIATE",
        "defaultDurationWeeks": 2,
        "defaultWeeklyHours": 6,
        "difficultyPreference": "MEDIUM",
        "interviewOriented": True,
        "targetAudience": "二分题经常卡在左右边界、循环条件或答案空间的学习者。",
        "prerequisites": ["理解有序数组和基本循环", "能独立写出基础二分查找"],
        "recommendedFor": ["需要短周期提升边界题稳定性", "旋转数组、矩阵搜索或答案二分易错"],
        "notRecommendedFor": ["还没掌握数组遍历", "想系统学习所有数据结构"],
        "expectedOutcome": "完成后能说清闭区间/半开区间模板，并能识别值域二分和索引二分的差异。",
        "source": HALFROST_SOURCE,
        "sourceDescription": "以 halfrost Binary_Search 元数据为主，LeetCode Binary Search 官方计划只作外链和目标参考。",
        "curationNotes": "按基础二分、边界/旋转数组、答案二分和矩阵/区间变体重建 2 周计划。",
        "metadata": {
            "sourceStrategy": "mixed_direct_metadata_and_reference_rebuild",
            "secondarySources": [LEETCODE_OFFICIAL_SOURCE["name"], LOCAL_SOURCE["name"]],
        },
        "phases": [
            phase_spec("基础模板与边界", 1, "稳定基础二分、插入位置、开方和左右边界写法。", ["Binary Search"], [
                "binary-search", "search-insert-position", "sqrtx", "powx-n", "valid-perfect-square",
                "first-bad-version", "guess-number-higher-or-lower", "find-first-and-last-position-of-element-in-sorted-array",
                "find-peak-element",
            ]),
            phase_spec("旋转、矩阵与答案二分", 1, "处理旋转数组、矩阵搜索和容量/速度类答案空间。", ["Binary Search", "Matrix"], [
                "search-a-2d-matrix", "search-a-2d-matrix-ii", "find-minimum-in-rotated-sorted-array",
                "search-in-rotated-sorted-array", "time-based-key-value-store", "median-of-two-sorted-arrays",
                "koko-eating-bananas", "capacity-to-ship-packages-within-d-days", "split-array-largest-sum",
            ]),
        ],
    },
    "topic_sliding_window_two_pointers": {
        "title": "滑动窗口与双指针专项计划",
        "summary": "2 周覆盖固定窗口、可变窗口、相向指针、快慢指针和排序去重组合。",
        "goal": "提升字符串和数组窗口题的建模与边界维护能力。",
        "intent": "TOPIC_BREAKTHROUGH",
        "level": "INTERMEDIATE",
        "defaultDurationWeeks": 2,
        "defaultWeeklyHours": 6,
        "difficultyPreference": "MEDIUM",
        "interviewOriented": True,
        "targetAudience": "字符串、数组和窗口维护题容易超时或边界出错的学习者。",
        "prerequisites": ["熟悉数组、字符串和哈希计数", "能写基础排序和双指针循环"],
        "recommendedFor": ["需要快速提升数组/字符串面试题正确率", "窗口收缩条件和去重逻辑不稳定"],
        "notRecommendedFor": ["还没掌握基础数组遍历", "主要薄弱点是图论或 DP"],
        "expectedOutcome": "完成后能区分固定窗口、可变窗口、相向指针和快慢指针，并能复盘窗口不变量。",
        "source": HALFROST_SOURCE,
        "sourceDescription": "以 halfrost Sliding_Window 和 Two_Pointers 元数据为主，NeetCode pattern 与 TIH 只作阶段结构参考。",
        "curationNotes": "多来源参考后重建；只内置 slug、题号、标签和自写推荐理由，不复制题解或文章。",
        "metadata": {
            "sourceStrategy": "mixed_direct_metadata_and_reference_rebuild",
            "secondarySources": [NEETCODE_SOURCE["name"], TIH_SOURCE["name"], LOCAL_SOURCE["name"]],
        },
        "phases": [
            phase_spec("双指针与排序去重", 1, "完成相向指针、快慢指针和排序后去重组合。", ["Two Pointers", "Array"], [
                "valid-palindrome", "two-sum-ii-input-array-is-sorted", "3sum", "container-with-most-water",
                "trapping-rain-water", "remove-duplicates-from-sorted-array", "move-zeroes", "minimum-size-subarray-sum",
            ]),
            phase_spec("固定与可变窗口", 1, "训练窗口扩张/收缩条件、计数器维护和单调队列窗口。", ["Sliding Window", "String", "Queue"], [
                "longest-substring-without-repeating-characters", "longest-repeating-character-replacement",
                "minimum-window-substring", "permutation-in-string", "find-all-anagrams-in-a-string",
                "sliding-window-maximum", "max-consecutive-ones-iii",
            ]),
        ],
    },
    "topic_tree_binary_tree_foundation": {
        "title": "树与二叉树专项",
        "summary": "3 周系统补齐树与二叉树基础，覆盖遍历、BST 性质、路径/LCA、构造和序列化。",
        "goal": "建立树递归返回值、层序遍历和二叉搜索树性质的稳定解题能力。",
        "intent": "TOPIC_BREAKTHROUGH",
        "level": "INTERMEDIATE",
        "defaultDurationWeeks": 3,
        "defaultWeeklyHours": 7,
        "difficultyPreference": "MEDIUM",
        "interviewOriented": True,
        "targetAudience": "已经做过基础数组和递归题，但树题递归边界、返回值或层序遍历不稳定的学习者。",
        "prerequisites": ["理解递归调用栈", "了解二叉树节点结构", "能写基础 DFS 或 BFS"],
        "recommendedFor": ["树遍历、BST 或路径题容易混淆", "需要面试前补齐二叉树高频题", "希望把递归和队列遍历整理成模板"],
        "notRecommendedFor": ["尚未掌握基础函数和递归", "主要薄弱点是字符串窗口或动态规划"],
        "expectedOutcome": "完成后能按遍历方式、BST 性质、路径问题和构造/序列化分类复盘树题。",
        "source": HALFROST_SOURCE,
        "sourceDescription": "以 halfrost Tree、Depth_First_Search、Breadth_First_Search 元数据和本地题库 tree/binary-tree 标签为基础重组。",
        "curationNotes": "直接内置可审计的题号、slug、难度、标签和来源链接；阶段说明、目标和复盘建议由 algo-mentor 自写。",
        "metadata": {
            "sourceStrategy": "mixed_halfrost_metadata_and_local_tag_rebuild",
            "secondarySources": [LOCAL_SOURCE["name"], LEETCODE_OFFICIAL_SOURCE["name"]],
        },
        "phases": [
            phase_spec("遍历与层序基础", 1, "用前中后序、层序和深度题稳定递归/队列遍历模板。", ["Tree", "Binary Tree", "Breadth-First Search"], [
                "binary-tree-preorder-traversal", "binary-tree-inorder-traversal", "binary-tree-postorder-traversal",
                "binary-tree-level-order-traversal", "binary-tree-zigzag-level-order-traversal",
                "maximum-depth-of-binary-tree", "minimum-depth-of-binary-tree", "invert-binary-tree",
            ]),
            phase_spec("BST 性质与构造", 1, "训练有序约束、迭代器、LCA 和由遍历序列重建树的边界。", ["Binary Search Tree", "Tree"], [
                "validate-binary-search-tree", "kth-smallest-element-in-a-bst",
                "lowest-common-ancestor-of-a-binary-search-tree", "binary-search-tree-iterator",
                "convert-sorted-array-to-binary-search-tree",
                "construct-binary-tree-from-preorder-and-inorder-traversal",
                "construct-binary-tree-from-inorder-and-postorder-traversal",
                "serialize-and-deserialize-bst",
            ]),
            phase_spec("路径、LCA 与序列化", 1, "集中处理路径累积、公共祖先、最大路径和树结构序列化。", ["Tree", "Depth-First Search"], [
                "path-sum", "path-sum-ii", "binary-tree-paths", "path-sum-iii",
                "lowest-common-ancestor-of-a-binary-tree", "binary-tree-maximum-path-sum",
                "serialize-and-deserialize-binary-tree", "flatten-binary-tree-to-linked-list",
            ]),
        ],
    },
    "topic_backtracking_foundation": {
        "title": "回溯专项突破",
        "summary": "3 周突破回溯搜索，按子集/组合/排列、棋盘与字符串切割、剪枝和约束搜索递进。",
        "goal": "掌握选择列表、路径状态、撤销选择和剪枝条件的表达方式。",
        "intent": "TOPIC_BREAKTHROUGH",
        "level": "INTERMEDIATE",
        "defaultDurationWeeks": 3,
        "defaultWeeklyHours": 7,
        "difficultyPreference": "MEDIUM",
        "interviewOriented": True,
        "targetAudience": "能写递归但回溯题容易漏撤销、去重或剪枝条件的学习者。",
        "prerequisites": ["理解递归和数组/字符串遍历", "能说明基础 DFS 结束条件", "做过至少 20 道 Easy/Medium 题"],
        "recommendedFor": ["组合、排列、子集题容易套错模板", "棋盘搜索或字符串切割题缺少系统复盘", "需要面试前集中补强搜索题"],
        "notRecommendedFor": ["还不能独立写递归", "只想练 SQL 或实现基础题"],
        "expectedOutcome": "完成后能把回溯题拆成选择、约束、终止、去重和剪枝五类复盘项。",
        "source": HALFROST_SOURCE,
        "sourceDescription": "以 halfrost Backtracking 元数据和本地题库 backtracking 标签为基础，按面试常见搜索模式重组。",
        "curationNotes": "只使用题号、slug、难度、标签和来源链接；不复制题解、文章正文、图示或代码。",
        "metadata": {
            "sourceStrategy": "mixed_halfrost_metadata_and_local_tag_rebuild",
            "secondarySources": [LOCAL_SOURCE["name"], NEETCODE_SOURCE["name"]],
        },
        "phases": [
            phase_spec("子集、组合与排列", 1, "先稳定选择树、去重边界和结果收集位置。", ["Backtracking", "Array"], [
                "subsets", "subsets-ii", "combinations", "combination-sum", "combination-sum-ii", "permutations", "permutations-ii",
            ]),
            phase_spec("棋盘与字符串切割", 1, "通过棋盘、括号、IP 和回文切割练习约束判断。", ["Backtracking", "String", "Matrix"], [
                "n-queens", "n-queens-ii", "sudoku-solver", "word-search", "palindrome-partitioning",
                "restore-ip-addresses", "generate-parentheses",
            ]),
            phase_spec("剪枝与约束搜索", 1, "用高约束题训练排序剪枝、状态压缩前置和搜索空间控制。", ["Backtracking", "Pruning"], [
                "combination-sum-iii", "letter-combinations-of-a-phone-number", "matchsticks-to-square",
                "partition-to-k-equal-sum-subsets", "expression-add-operators", "word-search-ii",
            ]),
        ],
    },
    "topic_heap_priority_queue": {
        "title": "堆与优先队列专项",
        "summary": "2 周集中训练 TopK、第 K 大、多路合并、调度和双堆中位数。",
        "goal": "掌握堆的排序视角、在线维护和多路合并建模方式。",
        "intent": "TOPIC_BREAKTHROUGH",
        "level": "INTERMEDIATE",
        "defaultDurationWeeks": 2,
        "defaultWeeklyHours": 6,
        "difficultyPreference": "MEDIUM",
        "interviewOriented": True,
        "targetAudience": "知道堆结构但在 TopK、数据流或调度题中不清楚堆元素设计的学习者。",
        "prerequisites": ["理解数组和排序", "知道最小堆/最大堆基本操作", "能分析 O(log n) 插入删除"],
        "recommendedFor": ["TopK、合并链表或数据流题不稳定", "需要快速补齐优先队列面试题", "想训练堆元素设计和懒删除意识"],
        "notRecommendedFor": ["还没掌握基础数组和排序", "主要薄弱点是树递归或回溯"],
        "expectedOutcome": "完成后能判断何时用堆、堆里放什么字段、何时需要双堆或延迟删除。",
        "source": HALFROST_SOURCE,
        "sourceDescription": "以本地题库 heap-priority-queue 标签为主，并结合 halfrost 可匹配元数据做来源审计。",
        "curationNotes": "按 TopK/第 K 大、多路合并、调度和双堆重建 2 周计划，只内置结构化题单元数据。",
        "metadata": {
            "sourceStrategy": "local_tag_rebuild_with_halfrost_audit",
            "secondarySources": [LOCAL_SOURCE["name"], NEETCODE_SOURCE["name"], TIH_SOURCE["name"]],
        },
        "phases": [
            phase_spec("TopK、第 K 大与多路合并", 1, "用静态数组、矩阵、链表和多列表题稳定堆排序模型。", ["Heap", "Priority Queue", "Sorting"], [
                "kth-largest-element-in-an-array", "top-k-frequent-elements", "top-k-frequent-words",
                "k-closest-points-to-origin", "kth-smallest-element-in-a-sorted-matrix", "merge-k-sorted-lists",
                "find-k-pairs-with-smallest-sums", "smallest-range-covering-elements-from-k-lists",
                "find-k-closest-elements",
            ]),
            phase_spec("调度、数据流与双堆", 1, "训练动态维护、任务调度、双堆平衡和在线更新。", ["Heap", "Priority Queue", "Design"], [
                "find-median-from-data-stream", "sliding-window-median", "task-scheduler", "reorganize-string",
                "ipo", "course-schedule-iii", "last-stone-weight", "kth-largest-element-in-a-stream",
                "reduce-array-size-to-the-half",
            ]),
        ],
    },
    "topic_greedy_strategies": {
        "title": "贪心策略专项",
        "summary": "3 周覆盖区间调度、跳跃/加油站、分配/找零和字符串排序贪心。",
        "goal": "建立局部最优选择、排序依据和反例检查的贪心证明能力。",
        "intent": "TOPIC_BREAKTHROUGH",
        "level": "INTERMEDIATE",
        "defaultDurationWeeks": 3,
        "defaultWeeklyHours": 7,
        "difficultyPreference": "MEDIUM",
        "interviewOriented": True,
        "targetAudience": "能写实现但贪心题经常无法证明选择策略或排序依据的学习者。",
        "prerequisites": ["熟悉数组、排序和基础区间题", "能写简单复杂度分析", "做过若干 Medium 题"],
        "recommendedFor": ["区间调度、跳跃题或字符串贪心经常靠直觉", "需要面试前整理贪心证明模板", "想补齐排序后选择类题"],
        "notRecommendedFor": ["还没掌握数组遍历和排序", "主要薄弱点是 DP 状态定义"],
        "expectedOutcome": "完成后能用排序依据、交换论证、覆盖范围或反例检查说明贪心正确性。",
        "source": HALFROST_SOURCE,
        "sourceDescription": "以本地题库 greedy、sorting 和区间相关标签为主，并结合 halfrost Sorting 元数据做来源审计。",
        "curationNotes": "按区间、跳跃/分配、字符串排序三类重建；不复制第三方题解或文章内容。",
        "metadata": {
            "sourceStrategy": "local_tag_rebuild_with_halfrost_audit",
            "secondarySources": [LOCAL_SOURCE["name"], LEETCODE_MASTER_SOURCE["name"], TIH_SOURCE["name"]],
        },
        "phases": [
            phase_spec("区间调度与覆盖", 1, "用合并、插入、删除、箭数和链式选择题训练排序依据。", ["Greedy", "Intervals", "Sorting"], [
                "merge-intervals", "insert-interval", "non-overlapping-intervals",
                "minimum-number-of-arrows-to-burst-balloons", "queue-reconstruction-by-height",
                "maximum-length-of-pair-chain", "course-schedule-iii", "partition-labels",
            ]),
            phase_spec("跳跃、加油站与分配", 1, "通过覆盖范围、资源分配和交易题训练局部最优边界。", ["Greedy", "Array"], [
                "jump-game", "jump-game-ii", "gas-station", "candy", "assign-cookies", "can-place-flowers",
                "best-time-to-buy-and-sell-stock-ii", "lemonade-change",
            ]),
            phase_spec("字符串与排序贪心", 1, "集中处理字典序、删除、重排和排序后组合的贪心选择。", ["Greedy", "String", "Sorting"], [
                "remove-duplicate-letters", "remove-k-digits", "reorganize-string", "largest-number",
                "monotone-increasing-digits", "increasing-triplet-subsequence", "hand-of-straights",
                "split-array-largest-sum",
            ]),
        ],
    },
    "topic_stack_monotonic": {
        "title": "栈与单调栈专项",
        "summary": "2 周补齐基础栈、表达式求值、单调栈和单调队列窗口题。",
        "goal": "掌握栈状态维护、表达式解析和单调结构的入栈/出栈不变量。",
        "intent": "TOPIC_BREAKTHROUGH",
        "level": "INTERMEDIATE",
        "defaultDurationWeeks": 2,
        "defaultWeeklyHours": 6,
        "difficultyPreference": "MEDIUM",
        "interviewOriented": True,
        "targetAudience": "括号、表达式、下一个更大元素或柱状图题容易写乱边界的学习者。",
        "prerequisites": ["理解数组和字符串遍历", "会使用栈或双端队列", "能写基础括号匹配"],
        "recommendedFor": ["单调栈入栈出栈条件不稳定", "表达式题和括号题缺少统一模板", "想补齐窗口最大值和柱状图经典题"],
        "notRecommendedFor": ["还不能使用基础数据结构", "主要目标是 SQL 或系统设计"],
        "expectedOutcome": "完成后能复述栈保存的状态含义，并能为单调栈题写出不变量和结算时机。",
        "source": HALFROST_SOURCE,
        "sourceDescription": "以 halfrost Stack 元数据和本地题库 stack/monotonic-stack/monotonic-queue 标签重组。",
        "curationNotes": "直接内置题号、slug、难度和标签；阶段目标、验收和复盘建议由 algo-mentor 自写。",
        "metadata": {
            "sourceStrategy": "mixed_halfrost_metadata_and_local_tag_rebuild",
            "secondarySources": [LOCAL_SOURCE["name"], NEETCODE_SOURCE["name"]],
        },
        "phases": [
            phase_spec("基础栈、括号与表达式", 1, "稳定括号匹配、最小栈、路径解析和表达式求值。", ["Stack", "String"], [
                "valid-parentheses", "min-stack", "implement-stack-using-queues", "implement-queue-using-stacks",
                "simplify-path", "evaluate-reverse-polish-notation", "basic-calculator", "basic-calculator-ii",
                "decode-string",
            ]),
            phase_spec("单调栈与单调队列", 1, "训练下一个更大元素、柱状图、接雨水和窗口队列不变量。", ["Monotonic Stack", "Monotonic Queue"], [
                "daily-temperatures", "next-greater-element-i", "next-greater-element-ii",
                "largest-rectangle-in-histogram", "maximal-rectangle", "trapping-rain-water",
                "online-stock-span", "sliding-window-maximum", "shortest-subarray-with-sum-at-least-k",
            ]),
        ],
    },
    "topic_bit_manipulation": {
        "title": "位运算专项",
        "summary": "2 周覆盖基础位操作、异或技巧、子集枚举、位掩码和进阶图状态题。",
        "goal": "掌握常见位操作、异或性质、bitmask 状态表示和枚举边界。",
        "intent": "TOPIC_BREAKTHROUGH",
        "level": "INTERMEDIATE",
        "defaultDurationWeeks": 2,
        "defaultWeeklyHours": 6,
        "difficultyPreference": "MEDIUM",
        "interviewOriented": True,
        "targetAudience": "位运算语法会用但对异或、mask 枚举和状态压缩缺少系统理解的学习者。",
        "prerequisites": ["理解二进制表示", "会写数组遍历和哈希计数", "知道与或非异或基础操作"],
        "recommendedFor": ["Single Number、Counting Bits 或 mask 题经常靠记忆", "需要短周期补齐位运算面试题", "想为状压 DP 打基础"],
        "notRecommendedFor": ["完全没学过二进制表示", "主要薄弱点是树或字符串窗口"],
        "expectedOutcome": "完成后能按位测试、位清除、异或分组、mask 枚举和状态压缩分类复盘题目。",
        "source": HALFROST_SOURCE,
        "sourceDescription": "以 halfrost Bit_Manipulation 元数据和本地题库 bit-manipulation/bitmask 标签重组。",
        "curationNotes": "只内置题号、slug、难度、标签和来源链接；不复制任何题解、题面或源码。",
        "metadata": {
            "sourceStrategy": "mixed_halfrost_metadata_and_local_tag_rebuild",
            "secondarySources": [LOCAL_SOURCE["name"], LEETCODE_OFFICIAL_SOURCE["name"]],
        },
        "phases": [
            phase_spec("基础位操作与异或技巧", 1, "用 Single Number、bit count 和幂判断题稳定常见位操作。", ["Bit Manipulation"], [
                "single-number", "single-number-ii", "single-number-iii", "number-of-1-bits", "reverse-bits",
                "counting-bits", "power-of-two", "power-of-four", "missing-number",
            ]),
            phase_spec("位掩码、子集枚举与进阶状态", 1, "训练子集生成、位与范围、异或 Trie 和状态压缩图搜索。", ["Bit Manipulation", "Bitmask"], [
                "subsets", "subsets-ii", "gray-code", "maximum-xor-of-two-numbers-in-an-array",
                "bitwise-and-of-numbers-range", "repeated-dna-sequences", "maximum-product-of-word-lengths",
                "utf-8-validation", "shortest-path-visiting-all-nodes",
            ]),
        ],
    },
    "leetcode_top_100_liked_revision": {
        "title": "Top 100 Liked 复盘计划",
        "summary": "6 周二刷和查漏补缺路线，覆盖高赞经典题中的数组、链表、树、图、动态规划和综合专题。",
        "goal": "围绕高赞经典题完成一轮面试前复盘和错题分类。",
        "intent": "MISTAKE_REVIEW",
        "level": "INTERMEDIATE",
        "defaultDurationWeeks": 6,
        "defaultWeeklyHours": 8,
        "difficultyPreference": "MIXED",
        "interviewOriented": True,
        "targetAudience": "已刷过一轮基础题、希望通过经典高赞题二刷查漏补缺的学习者。",
        "prerequisites": ["至少完成过 50 道题", "有错题复盘记录或薄弱标签", "能阅读 Medium 题解并复述思路"],
        "recommendedFor": ["面试前 1 到 2 个月复盘", "需要覆盖高频经典题并整理错题"],
        "notRecommendedFor": ["完全零基础", "只想用少量题做快速入门"],
        "expectedOutcome": "完成后能按主题复盘高赞经典题，并把错题归入数组、链表、树图、DP 和综合题型。",
        "source": LEETCODE_OFFICIAL_SOURCE,
        "sourceDescription": "LeetCode Top 100 Liked 官方计划只作外链参考；题目路线按本地题库和经典高赞标签重建。",
        "curationNotes": "不直接复制官方题单全文或题面；按本地题库 slug 组织二刷路线，metadata 标记复盘导向。",
        "metadata": {
            "sourceStrategy": "official_reference_after_rebuild",
            "secondarySources": [TIH_SOURCE["name"], LOCAL_SOURCE["name"]],
        },
        "phases": [
            phase_spec("数组、字符串与哈希复盘", 1, "先复盘最高频线性结构，整理哈希、窗口和前缀关系。", ["Array", "Hash Table", "String"], [
                "two-sum", "longest-substring-without-repeating-characters", "longest-palindromic-substring",
                "container-with-most-water", "3sum", "letter-combinations-of-a-phone-number", "valid-parentheses",
                "group-anagrams", "maximum-subarray", "spiral-matrix", "merge-intervals", "minimum-window-substring",
            ]),
            phase_spec("链表、栈与排序变体", 1, "复盘链表改写、括号/栈结构和排序后组合题。", ["Linked List", "Stack", "Sorting"], [
                "add-two-numbers", "remove-nth-node-from-end-of-list", "merge-two-sorted-lists", "merge-k-sorted-lists",
                "next-permutation", "search-in-rotated-sorted-array", "sort-colors", "linked-list-cycle",
                "reverse-linked-list", "intersection-of-two-linked-lists",
            ]),
            phase_spec("树与递归结构", 1, "集中复盘递归返回值、层序遍历、构造和树形 DP 前置。", ["Tree", "Binary Tree"], [
                "validate-binary-search-tree", "binary-tree-level-order-traversal", "maximum-depth-of-binary-tree",
                "construct-binary-tree-from-preorder-and-inorder-traversal", "flatten-binary-tree-to-linked-list",
                "invert-binary-tree", "lowest-common-ancestor-of-a-binary-tree", "diameter-of-binary-tree",
                "merge-two-binary-trees",
            ]),
            phase_spec("搜索、图与堆", 1, "复盘回溯、图搜索、拓扑和堆相关经典题。", ["Backtracking", "Graph", "Heap"], [
                "generate-parentheses", "combination-sum", "permutations", "subsets", "word-search",
                "number-of-islands", "course-schedule", "top-k-frequent-elements", "kth-largest-element-in-an-array",
            ]),
            phase_spec("动态规划与状态复盘", 1, "按一维、背包、字符串和区间/回文重新整理 DP 错题。", ["Dynamic Programming"], [
                "unique-paths", "climbing-stairs", "decode-ways", "word-break", "house-robber",
                "maximum-product-subarray", "longest-increasing-subsequence", "coin-change",
                "partition-equal-subset-sum", "palindromic-substrings", "edit-distance",
            ]),
            phase_spec("综合收尾与模拟面试", 1, "用难度混合题检查边界、复杂度和口述表达。", ["Binary Search", "Design", "Stack"], [
                "median-of-two-sorted-arrays", "rotate-image", "jump-game", "subarray-sum-equals-k",
                "sliding-window-maximum", "search-a-2d-matrix-ii", "perfect-squares", "move-zeroes",
                "find-the-duplicate-number", "decode-string", "daily-temperatures", "min-stack",
                "product-of-array-except-self", "best-time-to-buy-and-sell-stock", "majority-element",
                "shortest-unsorted-continuous-subarray",
            ]),
        ],
    },
    "programming_skills_implementation_foundation": {
        "title": "编程基础与实现力计划",
        "summary": "4 周补齐基础实现能力，覆盖模拟、字符串、数组、矩阵、基础数据结构和简单设计题。",
        "goal": "通过 Easy/Medium 本地题库提升换语言或基础实现薄弱时的编码稳定性。",
        "intent": "PRACTICE_GOAL",
        "level": "BEGINNER",
        "defaultDurationWeeks": 4,
        "defaultWeeklyHours": 5,
        "difficultyPreference": "EASY",
        "interviewOriented": False,
        "targetAudience": "刚换语言、基础语法不稳或实现细节经常出错的学习者。",
        "prerequisites": ["能写变量、循环、函数和基础数组操作", "愿意用同一语言反复练习基础实现"],
        "recommendedFor": ["想先提升代码熟练度", "Easy 题经常因为语法或边界出错", "准备进入系统刷题前打基础"],
        "notRecommendedFor": ["已经稳定完成 Medium/Hard 并只想挑战高阶算法", "主要目标是数据库或前端专项"],
        "expectedOutcome": "完成后能稳定实现基础数组、字符串、矩阵、栈和简单设计题，为后续面试路线打底。",
        "source": LEETCODE_OFFICIAL_SOURCE,
        "sourceDescription": "LeetCode Programming Skills 官方计划只作目标参考，题目由本地 Easy/Medium 题库重建。",
        "curationNotes": "官方计划仅作为外链和目标描述；具体路线按本地题库 slug 组织，避免复制官方内容。",
        "metadata": {
            "sourceStrategy": "official_reference_after_rebuild",
            "secondarySources": [LOCAL_SOURCE["name"]],
        },
        "phases": [
            phase_spec("语法热身与简单模拟", 1, "用短题稳定循环、条件分支、计数和简单数组返回。", ["Simulation", "Array"], [
                "fizz-buzz", "richest-customer-wealth", "running-sum-of-1d-array", "shuffle-the-array",
                "kids-with-the-greatest-number-of-candies", "number-of-good-pairs",
            ]),
            phase_spec("字符串与哈希基础", 1, "训练字符串遍历、拼接、映射关系和计数字典。", ["String", "Hash Table"], [
                "defanging-an-ip-address", "jewels-and-stones", "goal-parser-interpretation", "merge-strings-alternately",
                "valid-palindrome", "reverse-string", "reverse-words-in-a-string", "length-of-last-word",
                "roman-to-integer", "valid-anagram", "isomorphic-strings", "ransom-note",
            ]),
            phase_spec("数组、矩阵与边界", 1, "练习下标移动、矩阵遍历、原地修改和基础排序合并。", ["Array", "Matrix", "Two Pointers"], [
                "matrix-diagonal-sum", "transpose-matrix", "reshape-the-matrix", "spiral-matrix", "set-matrix-zeroes",
                "merge-sorted-array", "move-zeroes", "remove-element", "plus-one", "contains-duplicate", "summary-ranges",
            ]),
            phase_spec("基础数据结构实现", 1, "完成栈、队列、HashMap 和简单设计题，巩固类和状态维护。", ["Stack", "Queue", "Design"], [
                "valid-parentheses", "implement-queue-using-stacks", "min-stack", "design-hashmap", "design-parking-system",
            ]),
        ],
    },
}

TEMPLATE_ORDER = [
    "neetcode_150_systematic_interview",
    "neetcode_blind_75_interview_core",
    "tih_best_practice_50_5weeks",
    "cn_algorithm_foundation_12weeks",
    "topic_dynamic_programming_foundation",
    "topic_graph_bfs_dfs",
    "topic_binary_search_boundaries",
    "topic_sliding_window_two_pointers",
    "topic_tree_binary_tree_foundation",
    "topic_backtracking_foundation",
    "topic_heap_priority_queue",
    "topic_greedy_strategies",
    "topic_stack_monotonic",
    "topic_bit_manipulation",
    "leetcode_top_100_liked_revision",
    "programming_skills_implementation_foundation",
]

P1_A_BATCH_ONE_TEMPLATE_IDS = [
    "topic_tree_binary_tree_foundation",
    "topic_backtracking_foundation",
    "topic_heap_priority_queue",
    "topic_greedy_strategies",
    "topic_stack_monotonic",
    "topic_bit_manipulation",
]


@dataclass
class SourceData:
    neetcode_rows: list[dict[str, Any]]
    tih_markdown: str
    halfrost_meta: dict[str, str]


class ProblemIndex:
    def __init__(self, rows: Iterable[dict[str, Any]]) -> None:
        self.by_slug: dict[str, dict[str, Any]] = {}
        self.slug_by_frontend_id: dict[int, str] = {}
        for row in rows:
            slug = clean_text(row.get("slug"))
            if not slug:
                continue
            self.by_slug[slug] = row
            frontend_id = row.get("frontendId")
            if isinstance(frontend_id, int):
                self.slug_by_frontend_id[frontend_id] = slug

    @classmethod
    def from_path(cls, path: Path) -> "ProblemIndex":
        if not path.exists():
            return cls([])
        return cls(read_jsonl(path))

    @classmethod
    def from_slugs(cls, slugs: Iterable[str]) -> "ProblemIndex":
        rows = []
        for index, slug in enumerate(slugs, start=1):
            rows.append({
                "slug": slug,
                "frontendId": index,
                "frontendDisplayId": str(index),
                "titleEn": title_from_slug(slug),
                "titleZh": title_from_slug(slug),
                "difficulty": "MEDIUM",
                "tagValues": [],
                "leetcodeUrl": leetcode_url(slug),
            })
        return cls(rows)

    def has_slug(self, slug: str) -> bool:
        return slug in self.by_slug

    def find_slug_by_frontend_id(self, frontend_id: int) -> str | None:
        return self.slug_by_frontend_id.get(frontend_id)

    def problem(self, slug: str) -> dict[str, Any] | None:
        return self.by_slug.get(slug)

    def title(self, slug: str, fallback: str = "") -> str:
        row = self.problem(slug)
        if not row:
            return fallback or title_from_slug(slug)
        return clean_text(row.get("titleEn")) or clean_text(row.get("titleZh")) or fallback or title_from_slug(slug)

    def difficulty(self, slug: str, fallback: str = "Medium") -> str:
        row = self.problem(slug)
        if not row:
            return difficulty(fallback)
        return difficulty(row.get("difficulty"))

    def frontend_id(self, slug: str) -> int | None:
        row = self.problem(slug)
        value = row.get("frontendId") if row else None
        return value if isinstance(value, int) else None

    def tag_values(self, slug: str) -> list[str]:
        row = self.problem(slug)
        values = row.get("tagValues") if row else []
        return [clean_text(value) for value in values or [] if clean_text(value)]


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate learning plan template seed from multiple sources.")
    parser.add_argument("--template-source-dir", default=str(DEFAULT_TEMPLATE_SOURCE_DIR))
    parser.add_argument("--template-order", default=str(DEFAULT_TEMPLATE_ORDER_PATH))
    parser.add_argument("--source-json", default="", help="Local NeetCode .problemSiteData.json path.")
    parser.add_argument("--source-url", default=NEETCODE_RAW_URL, help="NeetCode source URL.")
    parser.add_argument("--tih-markdown", default="", help="Local TIH best-practice-questions.md path.")
    parser.add_argument("--tih-url", default=TIH_RAW_URL, help="TIH best practice markdown URL.")
    parser.add_argument("--halfrost-meta-dir", default="", help="Local halfrost ctl/meta directory path.")
    parser.add_argument("--local-problems", default=str(DEFAULT_LOCAL_PROBLEMS_PATH))
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    parser.add_argument("--generated-at", default=DEFAULT_GENERATED_AT)
    args = parser.parse_args()

    template_source_dir = Path(args.template_source_dir) if args.template_source_dir else None
    template_order_path = Path(args.template_order) if args.template_order else None
    sources = SourceData(neetcode_rows=[], tih_markdown="", halfrost_meta={})
    if not template_source_dir or not template_source_dir.exists():
        sources = load_source_data(
            neetcode_path=Path(args.source_json) if args.source_json else None,
            neetcode_url=args.source_url,
            tih_path=Path(args.tih_markdown) if args.tih_markdown else None,
            tih_url=args.tih_url,
            halfrost_meta_dir=Path(args.halfrost_meta_dir) if args.halfrost_meta_dir else None,
        )
    problem_index = ProblemIndex.from_path(Path(args.local_problems))
    templates, refs, report = build_seed(
        sources,
        problem_index,
        args.generated_at,
        template_source_dir=template_source_dir,
        template_order_path=template_order_path,
    )
    write_seed(Path(args.output_dir), templates, refs, report)
    print({
        "templates": report["templateCount"],
        "problemRefs": report["problemRefCount"],
        "matched": report["matchedProblemCount"],
        "missing": report["missingProblemCount"],
    })


def load_source_data(
    neetcode_path: Path | None = None,
    neetcode_url: str = NEETCODE_RAW_URL,
    tih_path: Path | None = None,
    tih_url: str = TIH_RAW_URL,
    halfrost_meta_dir: Path | None = None,
) -> SourceData:
    return SourceData(
        neetcode_rows=load_json_source(neetcode_path, neetcode_url),
        tih_markdown=load_text_source(tih_path, tih_url),
        halfrost_meta=load_halfrost_meta(halfrost_meta_dir),
    )


def load_json_source(path: Path | None, url: str) -> list[dict[str, Any]]:
    if path and path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    with urllib.request.urlopen(url, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def load_text_source(path: Path | None, url: str) -> str:
    if path and path.exists():
        return path.read_text(encoding="utf-8")
    with urllib.request.urlopen(url, timeout=30) as response:
        return response.read().decode("utf-8")


def load_halfrost_meta(meta_dir: Path | None) -> dict[str, str]:
    meta: dict[str, str] = {}
    for name in HALFROST_META_FILES:
        if meta_dir and (meta_dir / name).exists():
            meta[name] = (meta_dir / name).read_text(encoding="utf-8")
        else:
            url = f"{HALFROST_RAW_BASE}/{name}"
            with urllib.request.urlopen(url, timeout=30) as response:
                meta[name] = response.read().decode("utf-8")
    return meta


def build_seed(
    sources: SourceData,
    problem_index: ProblemIndex,
    generated_at: str = DEFAULT_GENERATED_AT,
    template_source_dir: Path | None = DEFAULT_TEMPLATE_SOURCE_DIR,
    template_order_path: Path | None = DEFAULT_TEMPLATE_ORDER_PATH,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, Any]]:
    if template_source_dir and template_source_dir.exists():
        templates, refs = build_seed_from_template_sources(template_source_dir, problem_index, template_order_path)
    else:
        halfrost_by_frontend_id = parse_halfrost_meta(sources.halfrost_meta)
        templates_by_id: dict[str, dict[str, Any]] = {}
        refs_by_template: dict[str, list[dict[str, Any]]] = {}

        add_neetcode_templates(sources.neetcode_rows, problem_index, templates_by_id, refs_by_template)
        add_tih_template(sources.tih_markdown, problem_index, templates_by_id, refs_by_template)
        add_manual_templates(problem_index, halfrost_by_frontend_id, templates_by_id, refs_by_template)

        templates = [templates_by_id[template_id] for template_id in TEMPLATE_ORDER]
        refs = [
            ref
            for template_id in TEMPLATE_ORDER
            for ref in sorted(refs_by_template[template_id], key=lambda item: (item["phaseIndex"], item["sortOrder"], item["sourceOrder"]))
        ]
    validate_seed(templates, refs)
    report = build_report(templates, refs, generated_at)
    return templates, refs, report


def build_seed_from_template_sources(
    template_source_dir: Path,
    problem_index: ProblemIndex,
    template_order_path: Path | None = None,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    template_order = load_template_order(template_source_dir, template_order_path)
    templates: list[dict[str, Any]] = []
    refs_by_template: dict[str, list[dict[str, Any]]] = {}
    seen_template_ids: set[str] = set()
    for template_id in template_order:
        if template_id in seen_template_ids:
            raise ValueError(f"duplicate templateId in template order: {template_id}")
        seen_template_ids.add(template_id)
        template_dir = template_source_dir / template_id
        template_path = template_dir / TEMPLATE_SOURCE_TEMPLATE_FILE
        refs_path = template_dir / TEMPLATE_SOURCE_PROBLEM_REFS_FILE
        if not template_path.exists():
            raise ValueError(f"missing template source file: {template_path}")
        if not refs_path.exists():
            raise ValueError(f"missing template problem refs file: {refs_path}")
        source_template = json.loads(template_path.read_text(encoding="utf-8"))
        source_template_id = clean_text(source_template.get("templateId"))
        if source_template_id != template_id:
            raise ValueError(f"template directory and templateId differ: {template_id} != {source_template_id}")
        refs = [
            build_source_ref(template_id, source_ref, problem_index)
            for source_ref in read_jsonl(refs_path)
        ]
        templates.append(build_template_from_source_template(source_template, refs))
        refs_by_template[template_id] = refs
    refs = [
        ref
        for template_id in template_order
        for ref in sorted(refs_by_template[template_id], key=lambda item: (item["phaseIndex"], item["sortOrder"], item["sourceOrder"]))
    ]
    return templates, refs


def load_template_order(template_source_dir: Path, template_order_path: Path | None = None) -> list[str]:
    order_path = template_order_path or template_source_dir.parent / "template_order.json"
    if order_path.exists():
        payload = json.loads(order_path.read_text(encoding="utf-8"))
        template_ids = payload.get("templateIds") if isinstance(payload, dict) else payload
        if not isinstance(template_ids, list):
            raise ValueError(f"template order must be a list or contain templateIds: {order_path}")
        return [clean_text(template_id) for template_id in template_ids if clean_text(template_id)]
    return sorted(path.name for path in template_source_dir.iterdir() if path.is_dir())


def build_source_ref(
    template_id: str,
    source_ref: dict[str, Any],
    problem_index: ProblemIndex,
) -> dict[str, Any]:
    slug = normalize_slug(clean_text(source_ref.get("problemSlug")))
    metadata = dict(source_ref.get("metadata") or {})
    metadata.pop("matchedLocalProblem", None)
    if "sourceTags" in metadata:
        metadata["sourceTags"] = problem_index.tag_values(slug)
    return problem_ref(
        template_id=template_id,
        phase_index=int(source_ref.get("phaseIndex", 0)),
        sort_order=int(source_ref.get("sortOrder", 0)),
        source_order=int(source_ref.get("sourceOrder", 0)),
        slug=slug,
        source_title=clean_text(source_ref.get("sourceTitle")) or problem_index.title(slug),
        source_difficulty=difficulty(source_ref.get("sourceDifficulty") or problem_index.difficulty(slug)),
        pattern=clean_text(source_ref.get("pattern")),
        source_url=clean_text(source_ref.get("sourceUrl")) or leetcode_url(slug),
        matched=problem_index.has_slug(slug),
        metadata=metadata,
    )


def build_template_from_source_template(
    source_template: dict[str, Any],
    refs: list[dict[str, Any]],
) -> dict[str, Any]:
    metadata = dict(source_template.get("metadata") or {})
    for key in DERIVED_TEMPLATE_METADATA_KEYS:
        metadata.pop(key, None)
    matched_count = sum(1 for ref in refs if ref["metadata"]["matchedLocalProblem"])
    missing = [ref["problemSlug"] for ref in refs if not ref["metadata"]["matchedLocalProblem"]]
    metadata = {
        **metadata,
        "matchedProblemCount": matched_count,
        "missingProblemCount": len(missing),
        "missingProblems": missing,
        "sourceProblemCount": metadata.get("sourceProblemCount", len(refs)),
        "sourceTags": sorted({tag for ref in refs for tag in ref["metadata"].get("sourceTags", [])}),
        "sourceStrategy": metadata.get("sourceStrategy", "direct_metadata"),
    }
    phases = list(source_template.get("phases") or [])
    topic_preferences = list(dict.fromkeys(tag for phase in phases for tag in phase.get("recommendedTags", [])))
    template = {
        **source_template,
        "topicPreferences": source_template.get("topicPreferences") or topic_preferences,
        "difficultyMix": difficulty_mix(refs),
        "metadata": metadata,
    }
    validate_template(template)
    return template


def add_neetcode_templates(
    source_rows: list[dict[str, Any]],
    problem_index: ProblemIndex,
    templates_by_id: dict[str, dict[str, Any]],
    refs_by_template: dict[str, list[dict[str, Any]]],
) -> None:
    for template_id in ["neetcode_150_systematic_interview", "neetcode_blind_75_interview_core"]:
        config = TEMPLATE_CONFIGS[template_id]
        source_flag = str(config["sourceFlag"])
        route_rows = [row for row in source_rows if row.get(source_flag) is True]
        refs = build_neetcode_refs(template_id, route_rows, problem_index)
        template = build_template_from_config(template_id, config, refs, build_neetcode_phases(template_id), {
            "sourceFlag": source_flag,
            "sourceProblemCount": len(route_rows),
            "patternCounts": dict(Counter(clean_text(row.get("pattern")) for row in route_rows)),
        })
        templates_by_id[template_id] = template
        refs_by_template[template_id] = refs


def build_neetcode_refs(
    template_id: str,
    rows: list[dict[str, Any]],
    problem_index: ProblemIndex,
) -> list[dict[str, Any]]:
    phase_by_pattern = pattern_phase_index(template_id)
    sort_order_by_phase: dict[int, int] = defaultdict(int)
    refs: list[dict[str, Any]] = []
    for source_order, row in enumerate(rows, start=1):
        slug = neetcode_problem_slug(row)
        pattern = clean_text(row.get("pattern"))
        phase_index = phase_by_pattern.get(pattern, len(NEETCODE_PHASES[template_id]))
        sort_order_by_phase[phase_index] += 1
        refs.append(problem_ref(
            template_id=template_id,
            phase_index=phase_index,
            sort_order=sort_order_by_phase[phase_index],
            source_order=source_order,
            slug=slug,
            source_title=clean_text(row.get("problem")),
            source_difficulty=difficulty(row.get("difficulty")),
            pattern=pattern,
            source_url=neetcode_problem_url(row),
            matched=problem_index.has_slug(slug),
            metadata={
                "sourceKey": NEETCODE_SOURCE["key"],
                "sourceName": NEETCODE_SOURCE["name"],
                "sourceLicense": "MIT",
                "neetcodePattern": pattern,
                "blind75": bool(row.get("blind75")),
                "neetcode150": bool(row.get("neetcode150")),
                "sourceFrontendId": frontend_id_from_code(row.get("code")),
                "code": clean_text(row.get("code")),
                "video": clean_text(row.get("video")),
            },
        ))
    return refs


def build_neetcode_phases(template_id: str) -> list[dict[str, Any]]:
    phases: list[dict[str, Any]] = []
    for index, phase in enumerate(NEETCODE_PHASES[template_id], start=1):
        patterns = list(phase["patterns"])
        phases.append(phase_row(
            index=index,
            title=str(phase["title"]),
            duration_weeks=int(phase["durationWeeks"]),
            focus=str(phase["focus"]),
            tags=patterns,
            objectives=[
                "完成本阶段核心题型的一轮训练",
                "为每类 pattern 记录至少 1 条可复用解题模板",
            ],
            acceptance=[
                "能独立说清本阶段推荐题的主解法和复杂度",
                "能复盘错题中的边界条件和状态定义",
            ],
            review="按题型整理错题，优先复盘相同 pattern 下重复出错的边界。",
        ))
    return phases


def add_tih_template(
    markdown: str,
    problem_index: ProblemIndex,
    templates_by_id: dict[str, dict[str, Any]],
    refs_by_template: dict[str, list[dict[str, Any]]],
) -> None:
    template_id = "tih_best_practice_50_5weeks"
    rows = parse_tih_best_practice(markdown)
    sort_order_by_phase: dict[int, int] = defaultdict(int)
    refs: list[dict[str, Any]] = []
    for source_order, row in enumerate(rows, start=1):
        phase_index = int(row["week"])
        sort_order_by_phase[phase_index] += 1
        slug = str(row["slug"])
        refs.append(problem_ref(
            template_id=template_id,
            phase_index=phase_index,
            sort_order=sort_order_by_phase[phase_index],
            source_order=source_order,
            slug=slug,
            source_title=str(row["title"]),
            source_difficulty=difficulty(row["difficulty"]),
            pattern=str(row["weekTitle"]),
            source_url=str(row["url"]),
            matched=problem_index.has_slug(slug),
            metadata={
                "sourceKey": TIH_SOURCE["key"],
                "sourceName": TIH_SOURCE["name"],
                "sourceLicense": "MIT",
                "sourceWeek": phase_index,
                "sourceWeekTitle": row["weekTitle"],
                "optional": bool(row["optional"]),
                "premium": bool(row["premium"]),
            },
        ))
    phases = [
        phase_row(1, "第 1 周：序列基础", 1, "用数组、字符串、哈希和基础区间题完成热身。", ["Array", "String", "Hash Table"], [
            "完成核心 Easy/Medium 序列题",
            "整理哈希计数、双指针和区间合并的复盘模板",
        ], ["能快速判断数组/字符串题的主要数据结构", "optional 题只作为加练项复盘"], "按错误类型区分下标、哈希计数和区间边界。"),
        phase_row(2, "第 2 周：基础数据结构", 1, "集中练习链表、窗口、图搜索和回文/字符串状态。", ["Linked List", "Sliding Window", "Graph"], [
            "完成链表指针、窗口和基础图搜索题",
            "复盘指针改写和窗口收缩条件",
        ], ["能解释链表题的指针移动顺序", "能说明 BFS/DFS visited 规则"], "把链表、窗口、图搜索错题分开整理。"),
        phase_row(3, "第 3 周：非线性结构", 1, "完成树、图、堆和区间中的面试高频题。", ["Tree", "Graph", "Heap", "Intervals"], [
            "完成树递归、拓扑和堆相关核心题",
            "复盘递归返回值和优先队列使用条件",
        ], ["能写出二叉树递归返回值定义", "能说明课程表拓扑建模"], "优先复盘树递归出口和图依赖关系。"),
        phase_row(4, "第 4 周：进阶结构", 1, "补齐 Trie、流式数据结构、并查集和区间变体。", ["Trie", "Heap", "Union Find"], [
            "完成 Trie、数据流、区间插入和连通性题",
            "明确 premium/optional 题的缺失处理和替代复盘",
        ], ["能独立描述 Trie 节点结构", "能说明堆和并查集适用条件"], "把 optional/premium 题作为同模式延伸，不阻塞主路线。"),
        phase_row(5, "第 5 周：动态规划", 1, "用一维、背包、序列和路径 DP 完成冲刺收尾。", ["Dynamic Programming", "Greedy"], [
            "完成 DP 核心题并记录状态定义",
            "把错题按一维、路径、字符串和选择集合分类",
        ], ["能说清每道 DP 题的状态含义和转移顺序", "能在复盘中标注初始化和边界"], "优先复盘状态定义不清的题，再补同类题。"),
    ]
    config = TEMPLATE_CONFIGS[template_id]
    template = build_template_from_config(template_id, config, refs, phases, {
        "sourceProblemCount": len(rows),
        "coreProblemCount": sum(1 for row in rows if not row["optional"]),
        "optionalProblemCount": sum(1 for row in rows if row["optional"]),
        "premiumProblemCount": sum(1 for row in rows if row["premium"]),
        "sourceStrategy": "direct_markdown_problem_metadata",
    })
    templates_by_id[template_id] = template
    refs_by_template[template_id] = refs


def parse_tih_best_practice(markdown: str) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    week = 0
    week_title = ""
    optional = False
    for line in markdown.splitlines():
        week_match = re.match(r"^## Week (\d+) - (.+)$", line.strip())
        if week_match:
            week = int(week_match.group(1))
            week_title = week_match.group(2).strip()
            optional = False
            continue
        if line.strip().startswith("#### Optional"):
            optional = True
            continue
        if not line.startswith("|") or "leetcode.com/problems/" not in line:
            continue
        parts = [part.strip() for part in line.strip().strip("|").split("|")]
        if len(parts) < 3 or parts[0] == "Question":
            continue
        match = re.search(r"https://leetcode\.com/problems/([^/)]+)", parts[2])
        if not match or week == 0:
            continue
        slug = normalize_slug(match.group(1))
        rows.append({
            "week": week,
            "weekTitle": week_title,
            "title": clean_markdown(parts[0]),
            "difficulty": clean_markdown(parts[1]),
            "slug": slug,
            "url": leetcode_url(slug),
            "optional": optional,
            "premium": "premium" in line.lower(),
        })
    return rows


def add_manual_templates(
    problem_index: ProblemIndex,
    halfrost_by_frontend_id: dict[int, dict[str, Any]],
    templates_by_id: dict[str, dict[str, Any]],
    refs_by_template: dict[str, list[dict[str, Any]]],
) -> None:
    for template_id in [
        "cn_algorithm_foundation_12weeks",
        "topic_dynamic_programming_foundation",
        "topic_graph_bfs_dfs",
        "topic_binary_search_boundaries",
        "topic_sliding_window_two_pointers",
        *P1_A_BATCH_ONE_TEMPLATE_IDS,
        "leetcode_top_100_liked_revision",
        "programming_skills_implementation_foundation",
    ]:
        config = MANUAL_TEMPLATES[template_id]
        refs = build_manual_refs(template_id, config["phases"], problem_index, halfrost_by_frontend_id)
        phases = [
            phase_row(
                index=index,
                title=phase["title"],
                duration_weeks=phase["durationWeeks"],
                focus=phase["focus"],
                tags=phase["recommendedTags"],
                objectives=phase["objectives"],
                acceptance=phase["acceptanceCriteria"],
                review=phase["reviewAdvice"],
            )
            for index, phase in enumerate(config["phases"], start=1)
        ]
        template = build_template_from_config(template_id, config, refs, phases, {
            "sourceProblemCount": len(refs),
            **config.get("metadata", {}),
        })
        templates_by_id[template_id] = template
        refs_by_template[template_id] = refs


def build_manual_refs(
    template_id: str,
    phase_specs: list[dict[str, Any]],
    problem_index: ProblemIndex,
    halfrost_by_frontend_id: dict[int, dict[str, Any]],
) -> list[dict[str, Any]]:
    refs: list[dict[str, Any]] = []
    seen: set[str] = set()
    source_order = 0
    for phase_index, phase in enumerate(phase_specs, start=1):
        sort_order = 0
        for item in phase["problemSlugs"]:
            slug = normalize_slug(item if isinstance(item, str) else item["slug"])
            repeat_reason = ""
            if slug in seen:
                repeat_reason = f"同一模板内用于 {phase['title']} 复盘闭环。"
            seen.add(slug)
            source_order += 1
            sort_order += 1
            frontend_id = problem_index.frontend_id(slug)
            halfrost = halfrost_by_frontend_id.get(frontend_id or -1)
            source_key = halfrost["sourceKey"] if halfrost else str(MANUAL_TEMPLATES[template_id]["source"]["key"])
            source_name = halfrost["sourceName"] if halfrost else str(MANUAL_TEMPLATES[template_id]["source"]["name"])
            refs.append(problem_ref(
                template_id=template_id,
                phase_index=phase_index,
                sort_order=sort_order,
                source_order=source_order,
                slug=slug,
                source_title=halfrost["sourceTitle"] if halfrost else problem_index.title(slug),
                source_difficulty=halfrost["sourceDifficulty"] if halfrost else problem_index.difficulty(slug),
                pattern=phase["title"],
                source_url=leetcode_url(slug),
                matched=problem_index.has_slug(slug),
                metadata={
                    "sourceKey": source_key,
                    "sourceName": source_name,
                    "sourceLicense": "MIT" if halfrost else "reference/local",
                    "sourceFrontendId": frontend_id,
                    "sourceTags": problem_index.tag_values(slug),
                    "sourceBucket": phase["title"],
                    "matchedBy": "slug",
                    "repeatReason": repeat_reason,
                    "halfrostMetaFiles": halfrost["metaFiles"] if halfrost else [],
                    "sourceStrategy": MANUAL_TEMPLATES[template_id].get("metadata", {}).get("sourceStrategy", "manual_rebuild"),
                },
            ))
    return refs


def parse_halfrost_meta(meta_by_file: dict[str, str]) -> dict[int, dict[str, Any]]:
    by_id: dict[int, dict[str, Any]] = {}
    pattern = re.compile(r"^\|(\d+)\.\s*([^|]+?)\s*\|.*?\|\s*(Easy|Medium|Hard)\s*\|", re.IGNORECASE)
    for file_name, text in meta_by_file.items():
        for line in text.splitlines():
            match = pattern.match(line.strip())
            if not match:
                continue
            frontend_id = int(match.group(1))
            title = clean_text(match.group(2))
            item = by_id.setdefault(frontend_id, {
                "sourceKey": HALFROST_SOURCE["key"],
                "sourceName": HALFROST_SOURCE["name"],
                "sourceTitle": title,
                "sourceDifficulty": difficulty(match.group(3)),
                "metaFiles": [],
            })
            if file_name not in item["metaFiles"]:
                item["metaFiles"].append(file_name)
    return by_id


def build_template_from_config(
    template_id: str,
    config: dict[str, Any],
    refs: list[dict[str, Any]],
    phases: list[dict[str, Any]],
    extra_metadata: dict[str, Any],
) -> dict[str, Any]:
    source = config["source"]
    matched_count = sum(1 for ref in refs if ref["metadata"]["matchedLocalProblem"])
    missing = [ref["problemSlug"] for ref in refs if not ref["metadata"]["matchedLocalProblem"]]
    metadata = {
        **extra_metadata,
        "matchedProblemCount": matched_count,
        "missingProblemCount": len(missing),
        "missingProblems": missing,
        "sourceProblemCount": extra_metadata.get("sourceProblemCount", len(refs)),
        "sourceTags": sorted({tag for ref in refs for tag in ref["metadata"].get("sourceTags", [])}),
        "sourceStrategy": extra_metadata.get("sourceStrategy", "direct_metadata"),
    }
    topic_preferences = list(dict.fromkeys(tag for phase in phases for tag in phase["recommendedTags"]))
    template = {
        "templateId": template_id,
        "title": config["title"],
        "summary": config["summary"],
        "intent": config["intent"],
        "goal": config["goal"],
        "defaultDurationWeeks": config["defaultDurationWeeks"],
        "level": config["level"],
        "defaultWeeklyHours": config["defaultWeeklyHours"],
        "programmingLanguage": "Java",
        "difficultyPreference": config["difficultyPreference"],
        "interviewOriented": config["interviewOriented"],
        "topicPreferences": topic_preferences,
        "targetAudience": config["targetAudience"],
        "difficultyMix": difficulty_mix(refs),
        "prerequisites": config["prerequisites"],
        "recommendedFor": config["recommendedFor"],
        "notRecommendedFor": config["notRecommendedFor"],
        "expectedOutcome": config["expectedOutcome"],
        "sourceName": source["name"],
        "sourceUrl": source["url"],
        "sourceCommit": source["commit"],
        "sourceDataPath": source["dataPath"],
        "sourceDescription": config["sourceDescription"],
        "curationNotes": config["curationNotes"],
        "licenseNotice": source["licenseNotice"],
        "metadata": metadata,
        "phases": phases,
    }
    validate_template(template)
    return template


def phase_row(
    index: int,
    title: str,
    duration_weeks: int,
    focus: str,
    tags: list[str],
    objectives: list[str],
    acceptance: list[str],
    review: str,
) -> dict[str, Any]:
    return {
        "phaseIndex": index,
        "title": title,
        "durationWeeks": duration_weeks,
        "focus": focus,
        "objectives": objectives,
        "recommendedTags": tags,
        "acceptanceCriteria": acceptance,
        "reviewAdvice": review,
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
    matched: bool,
    metadata: dict[str, Any],
) -> dict[str, Any]:
    return {
        "templateId": template_id,
        "phaseIndex": phase_index,
        "sortOrder": sort_order,
        "sourceOrder": source_order,
        "problemSlug": slug,
        "sourceTitle": source_title or title_from_slug(slug),
        "sourceDifficulty": difficulty(source_difficulty),
        "pattern": pattern,
        "sourceUrl": source_url,
        "metadata": without_none({
            **metadata,
            "matchedLocalProblem": matched,
        }),
    }


def build_report(
    templates: list[dict[str, Any]],
    refs: list[dict[str, Any]],
    generated_at: str,
) -> dict[str, Any]:
    template_reports = {template["templateId"]: template_report(template, [
        ref for ref in refs if ref["templateId"] == template["templateId"]
    ]) for template in templates}
    source_template_ids: dict[str, set[str]] = defaultdict(set)
    source_ref_counts: Counter[str] = Counter()
    source_matched_counts: Counter[str] = Counter()
    source_missing_counts: Counter[str] = Counter()
    for template in templates:
        source_template_ids[source_key_by_name(template["sourceName"])].add(template["templateId"])
        for secondary in template["metadata"].get("secondarySources", []):
            source_template_ids[source_key_by_name(str(secondary))].add(template["templateId"])
    for ref in refs:
        source_key = ref["metadata"].get("sourceKey", "unknown")
        source_ref_counts[source_key] += 1
        if ref["metadata"]["matchedLocalProblem"]:
            source_matched_counts[source_key] += 1
        else:
            source_missing_counts[source_key] += 1
    return {
        "schemaVersion": 1,
        "generatedAt": generated_at,
        "source": ROOT_SOURCE,
        "sources": [
            {
                "name": source["name"],
                "url": source["url"],
                "commitOrVersion": source["commit"],
                "dataPath": source["dataPath"],
                "licenseNotice": source["licenseNotice"],
                "templateIds": sorted(source_template_ids.get(source["key"], set())),
                "problemRefCount": source_ref_counts.get(source["key"], 0),
                "matchedProblemCount": source_matched_counts.get(source["key"], 0),
                "missingProblemCount": source_missing_counts.get(source["key"], 0),
            }
            for source in SOURCE_DEFINITIONS
            if source_template_ids.get(source["key"]) or source_ref_counts.get(source["key"], 0)
        ],
        "templateCount": len(templates),
        "problemRefCount": len(refs),
        "matchedProblemCount": sum(1 for ref in refs if ref["metadata"]["matchedLocalProblem"]),
        "missingProblemCount": sum(1 for ref in refs if not ref["metadata"]["matchedLocalProblem"]),
        "templates": template_reports,
    }


def source_key_by_name(name: str) -> str:
    for source in SOURCE_DEFINITIONS:
        if source["name"] == name:
            return str(source["key"])
    return "unknown"


def difficulty_mix(refs: list[dict[str, Any]]) -> dict[str, dict[str, int | float]]:
    counts = Counter(difficulty(ref["sourceDifficulty"]) for ref in refs)
    total = max(1, len(refs))
    return {
        key: {"count": counts.get(key, 0), "ratio": round(counts.get(key, 0) / total, 4)}
        for key in ["Easy", "Medium", "Hard"]
    }


def template_report(template: dict[str, Any], refs: list[dict[str, Any]]) -> dict[str, Any]:
    missing_problem_slugs = [
        ref["problemSlug"] for ref in refs if not ref["metadata"]["matchedLocalProblem"]
    ]
    return {
        "title": template["title"],
        "phaseCount": len(template["phases"]),
        "problemCount": len(refs),
        "matchedProblemCount": sum(1 for ref in refs if ref["metadata"]["matchedLocalProblem"]),
        "missingProblemCount": sum(1 for ref in refs if not ref["metadata"]["matchedLocalProblem"]),
        "missingProblemExamples": missing_problem_slugs[:10],
        "difficultyMix": template["difficultyMix"],
    }


def validate_seed(templates: list[dict[str, Any]], refs: list[dict[str, Any]]) -> None:
    templates_by_id: dict[str, dict[str, Any]] = {}
    phase_indexes_by_template: dict[str, set[int]] = {}
    for template in templates:
        template_id = str(template["templateId"])
        if template_id in templates_by_id:
            raise ValueError(f"duplicate templateId: {template_id}")
        validate_template(template)
        templates_by_id[template_id] = template
        phase_indexes_by_template[template_id] = {
            int(phase["phaseIndex"]) for phase in template["phases"]
        }

    ref_count_by_template: Counter[str] = Counter()
    source_orders_by_template: dict[str, set[int]] = defaultdict(set)
    slugs_by_template: dict[str, set[str]] = defaultdict(set)
    sort_orders_by_template_phase: dict[tuple[str, int], list[int]] = defaultdict(list)
    for ref in refs:
        template_id = clean_text(ref.get("templateId"))
        if template_id not in templates_by_id:
            raise ValueError(f"problem ref points to unknown template: {template_id}")
        phase_index = int(ref.get("phaseIndex", 0))
        if phase_index not in phase_indexes_by_template[template_id]:
            raise ValueError(f"problem ref points to unknown phase: {template_id}#{phase_index}")
        sort_order = int(ref.get("sortOrder", 0))
        source_order = int(ref.get("sourceOrder", 0))
        if sort_order < 1 or source_order < 1:
            raise ValueError(f"problem ref order must be positive: {template_id}")
        if source_order in source_orders_by_template[template_id]:
            raise ValueError(f"duplicate sourceOrder in template: {template_id}#{source_order}")
        source_orders_by_template[template_id].add(source_order)
        slug = clean_text(ref.get("problemSlug"))
        if not slug:
            raise ValueError(f"missing problemSlug in template: {template_id}")
        if slug in slugs_by_template[template_id] and not clean_text(ref.get("metadata", {}).get("repeatReason")):
            raise ValueError(f"duplicate problemSlug without repeatReason: {template_id}#{slug}")
        slugs_by_template[template_id].add(slug)
        for field in ["sourceTitle", "sourceDifficulty", "pattern", "sourceUrl"]:
            if not clean_text(ref.get(field)):
                raise ValueError(f"missing problem ref field {field}: {template_id}#{slug}")
        if contains_none(ref.get("metadata", {})):
            raise ValueError(f"problem ref metadata must not contain null values: {template_id}#{slug}")
        sort_orders_by_template_phase[(template_id, phase_index)].append(sort_order)
        ref_count_by_template[template_id] += 1

    for template_id in templates_by_id:
        if ref_count_by_template[template_id] == 0:
            raise ValueError(f"template has no problem refs: {template_id}")
        if not any(ref["metadata"].get("matchedLocalProblem") for ref in refs if ref["templateId"] == template_id):
            raise ValueError(f"template has no matched local problem refs: {template_id}")

    for (template_id, phase_index), sort_orders in sort_orders_by_template_phase.items():
        expected = list(range(1, len(sort_orders) + 1))
        if sorted(sort_orders) != expected:
            raise ValueError(f"sortOrder must be contiguous: {template_id}#{phase_index}")


def validate_template(template: dict[str, Any]) -> None:
    required_text_fields = [
        "templateId",
        "targetAudience",
        "title",
        "summary",
        "goal",
        "expectedOutcome",
        "sourceName",
        "sourceUrl",
        "sourceCommit",
        "sourceDataPath",
        "sourceDescription",
        "curationNotes",
        "licenseNotice",
    ]
    for field in required_text_fields:
        if not template.get(field):
            raise ValueError(f"missing required template field: {field}")
    for field in ["difficultyMix", "prerequisites", "recommendedFor", "notRecommendedFor", "phases"]:
        if not template.get(field):
            raise ValueError(f"missing required template field: {field}")
    if int(template.get("defaultDurationWeeks", 0)) < 1 or int(template.get("defaultWeeklyHours", 0)) < 1:
        raise ValueError(f"invalid template duration or weekly hours: {template.get('templateId')}")
    phase_indexes: list[int] = []
    duration_weeks = 0
    for phase in template["phases"]:
        phase_indexes.append(int(phase.get("phaseIndex", 0)))
        duration_weeks += int(phase.get("durationWeeks", 0))
        for field in ["title", "focus", "objectives", "recommendedTags", "acceptanceCriteria", "reviewAdvice"]:
            if not phase.get(field):
                raise ValueError(f"missing required phase field: {field}")
    if phase_indexes != list(range(1, len(phase_indexes) + 1)):
        raise ValueError(f"phaseIndex must be contiguous: {template.get('templateId')}")
    if duration_weeks != int(template["defaultDurationWeeks"]):
        raise ValueError(f"phase duration sum must equal defaultDurationWeeks: {template.get('templateId')}")


def write_seed(
    output_dir: Path,
    templates: list[dict[str, Any]],
    refs: list[dict[str, Any]],
    report: dict[str, Any],
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    write_jsonl(output_dir / TEMPLATES_FILE, templates)
    write_jsonl(output_dir / PROBLEM_REFS_FILE, refs)
    metadata_markdown = metadata_markdown_text(report)
    (output_dir / METADATA_FILE).write_text(metadata_markdown, encoding="utf-8")
    non_manifest_stats = {
        TEMPLATES_FILE: file_stats(output_dir / TEMPLATES_FILE),
        PROBLEM_REFS_FILE: file_stats(output_dir / PROBLEM_REFS_FILE),
        METADATA_FILE: file_stats(output_dir / METADATA_FILE),
    }
    manifest_payload_without_self = {**report, "files": non_manifest_stats}
    manifest_bytes = json.dumps(
        manifest_payload_without_self,
        ensure_ascii=False,
        indent=2,
        sort_keys=True,
    ).encode("utf-8")
    manifest = {
        **report,
        "files": {
            **non_manifest_stats,
            MANIFEST_FILE: {
                "path": MANIFEST_FILE,
                "bytes": len(manifest_bytes),
                "sha256": hashlib.sha256(manifest_bytes).hexdigest(),
                "hashInput": "manifest payload before self file stat insertion",
            },
        },
    }
    write_json(output_dir / MANIFEST_FILE, manifest)


def metadata_markdown_text(report: dict[str, Any]) -> str:
    lines = [
        "# 学习计划模板 Seed 元数据",
        "",
        f"- 生成时间：`{report['generatedAt']}`",
        f"- 根来源：`{report['source']['name']}`",
        f"- 模板数：`{report['templateCount']}`",
        f"- 题目引用数：`{report['problemRefCount']}`",
        f"- 本地题库匹配：`{report['matchedProblemCount']}`",
        f"- 本地题库缺失：`{report['missingProblemCount']}`",
        "",
        "## 来源归因",
        "",
    ]
    for source in report["sources"]:
        lines.extend([
            f"### {source['name']}",
            "",
            f"- URL：{source['url']}",
            f"- 固定版本：`{source['commitOrVersion']}`",
            f"- 源路径：`{source['dataPath']}`",
            f"- 覆盖模板：`{', '.join(source['templateIds']) if source['templateIds'] else '仅题目元数据引用'}`",
            f"- refs / matched / missing：`{source['problemRefCount']} / {source['matchedProblemCount']} / {source['missingProblemCount']}`",
            f"- 授权备注：{source['licenseNotice']}",
            "",
        ])
    lines.extend(["## 本批模板", ""])
    for template_id, item in report["templates"].items():
        lines.extend([
            f"### {template_id}",
            "",
            f"- 标题：{item['title']}",
            f"- 题目数：`{item['problemCount']}`",
            f"- 阶段数：`{item['phaseCount']}`",
            f"- 匹配题：`{item['matchedProblemCount']}`",
            f"- 缺失题：`{item['missingProblemCount']}`",
            f"- 缺失题示例：`{', '.join(item['missingProblemExamples']) if item['missingProblemExamples'] else '无'}`",
            f"- 难度分布：`{json.dumps(item['difficultyMix'], ensure_ascii=False)}`",
            "",
        ])
    lines.extend([
        "## 已知限制",
        "",
        "- 模板阶段是生成草稿的完整执行路线，草稿默认包含所有本地匹配题。",
        "- 缺失题只进入模板明细、导入审计和草稿 metadata，不进入草稿推荐题。",
        "- 官方网页、无 LICENSE 来源和文章型来源只作外链或结构参考，未复制题面、题解、文章正文、图示或代码。",
        "- 发布前仍需复核第三方来源授权边界，以及是否需要在前端对缺失题做轻提示。",
        "",
    ])
    return "\n".join(lines)


def file_stats(path: Path) -> dict[str, Any]:
    data = path.read_bytes()
    return {
        "path": path.name,
        "bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
    }


def pattern_phase_index(template_id: str) -> dict[str, int]:
    mapping: dict[str, int] = {}
    for index, phase in enumerate(NEETCODE_PHASES[template_id], start=1):
        for pattern in phase["patterns"]:
            mapping[str(pattern)] = index
    return mapping


def neetcode_problem_slug(row: dict[str, Any]) -> str:
    code = clean_text(row.get("code"))
    match = re.match(r"^\d+-(.+)$", code)
    if match:
        return normalize_slug(match.group(1))
    link = clean_text(row.get("link")).strip("/")
    return normalize_slug(link.split("/")[-1])


def neetcode_problem_url(row: dict[str, Any]) -> str:
    link = clean_text(row.get("link")).strip("/")
    return f"https://neetcode.io/problems/{link}"


def frontend_id_from_code(value: Any) -> int | None:
    match = re.match(r"^(\d+)-", clean_text(value))
    return int(match.group(1)) if match else None


def leetcode_url(slug: str) -> str:
    return f"https://leetcode.com/problems/{normalize_slug(slug)}/"


def normalize_slug(value: str) -> str:
    text = clean_text(value).split("?")[0].split("#")[0].strip("/")
    if "/" in text:
        text = text.split("/")[-1]
    return text.lower()


def title_from_slug(slug: str) -> str:
    return normalize_slug(slug).replace("-", " ").title()


def difficulty(value: Any) -> str:
    text = clean_text(value).upper()
    return {
        "EASY": "Easy",
        "MEDIUM": "Medium",
        "HARD": "Hard",
    }.get(text, "Medium")


def clean_text(value: Any) -> str:
    return "" if value is None else str(value).strip()


def clean_markdown(value: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", value)).strip()


def without_none(value: Any) -> Any:
    if isinstance(value, dict):
        return {
            key: without_none(item)
            for key, item in value.items()
            if item is not None
        }
    if isinstance(value, list):
        return [without_none(item) for item in value if item is not None]
    return value


def contains_none(value: Any) -> bool:
    if value is None:
        return True
    if isinstance(value, dict):
        return any(contains_none(item) for item in value.values())
    if isinstance(value, list):
        return any(contains_none(item) for item in value)
    return False


if __name__ == "__main__":
    main()
