#!/usr/bin/env python3
"""生成学习计划模板 seed。"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    sys.path.append(str(Path(__file__).resolve().parents[2]))

from tools.problem_seed.leetcode_api import read_jsonl, utc_now_iso, write_json, write_jsonl


SOURCE_NAME = "neetcode-gh/leetcode"
SOURCE_URL = "https://github.com/neetcode-gh/leetcode"
SOURCE_COMMIT = "9907b7fed441fa55083c0751e208b7197101dbba"
SOURCE_DATA_PATH = ".problemSiteData.json"
SOURCE_RAW_URL = (
    "https://raw.githubusercontent.com/neetcode-gh/leetcode/"
    f"{SOURCE_COMMIT}/{SOURCE_DATA_PATH}"
)
DEFAULT_OUTPUT_DIR = Path("data/learning-plan-template-seed")
DEFAULT_LOCAL_PROBLEMS_PATH = Path("data/seed/problems.jsonl")

TEMPLATES_FILE = "learning_plan_templates.jsonl"
PROBLEM_REFS_FILE = "learning_plan_template_problem_refs.jsonl"
MANIFEST_FILE = "learning_plan_template_seed_manifest.json"
METADATA_FILE = "learning_plan_template_seed_metadata.md"

PHASES = {
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
        {
            "title": "数组与哈希",
            "durationWeeks": 1,
            "patterns": ["Arrays & Hashing"],
            "focus": "完成 NeetCode 150 中数组与哈希题目，建立频次、去重和分组模型。",
        },
        {
            "title": "双指针与滑动窗口",
            "durationWeeks": 1,
            "patterns": ["Two Pointers", "Sliding Window"],
            "focus": "完成双指针和滑窗题目，训练窗口维护、左右边界和收缩条件。",
        },
        {
            "title": "栈与二分搜索",
            "durationWeeks": 1,
            "patterns": ["Stack", "Binary Search"],
            "focus": "完成栈和二分题目，训练单调结构、括号状态和边界搜索。",
        },
        {
            "title": "链表与区间",
            "durationWeeks": 1,
            "patterns": ["Linked List", "Intervals"],
            "focus": "完成链表和区间题目，训练指针改写、合并、插入和调度边界。",
        },
        {
            "title": "树与递归遍历",
            "durationWeeks": 1,
            "patterns": ["Trees"],
            "focus": "完成树题目，训练 DFS/BFS、递归返回值、序列化和二叉搜索树性质。",
        },
        {
            "title": "Trie 与堆",
            "durationWeeks": 1,
            "patterns": ["Tries", "Heap / Priority Queue"],
            "focus": "完成 Trie 和堆题目，训练前缀树建模、Top K 和优先级调度。",
        },
        {
            "title": "回溯",
            "durationWeeks": 1,
            "patterns": ["Backtracking"],
            "focus": "完成回溯题目，训练选择树、剪枝和状态恢复。",
        },
        {
            "title": "图搜索",
            "durationWeeks": 1,
            "patterns": ["Graphs"],
            "focus": "完成图搜索题目，训练连通块、拓扑关系、BFS/DFS 和网格建模。",
        },
        {
            "title": "进阶图与贪心",
            "durationWeeks": 1,
            "patterns": ["Advanced Graphs", "Greedy"],
            "focus": "完成进阶图和贪心题目，训练最短路、并查集和局部最优证明。",
        },
        {
            "title": "一维动态规划",
            "durationWeeks": 1,
            "patterns": ["1-D Dynamic Programming"],
            "focus": "完成一维动态规划题目，训练状态定义、转移顺序和滚动优化。",
        },
        {
            "title": "二维动态规划",
            "durationWeeks": 1,
            "patterns": ["2-D Dynamic Programming"],
            "focus": "完成二维动态规划题目，训练网格、区间和多维状态设计。",
        },
        {
            "title": "位运算、数学与综合收尾",
            "durationWeeks": 1,
            "patterns": ["Bit Manipulation", "Math & Geometry"],
            "focus": "完成位运算、数学几何和综合收尾题目，整理完整路线复盘材料。",
        },
    ],
}

TEMPLATE_CONFIGS = {
    "neetcode_blind_75_interview_core": {
        "sourceFlag": "blind75",
        "title": "NeetCode Blind 75 面试核心计划",
        "summary": "用 4 周跑完面试最核心的 75 道题路线，优先覆盖高频数据结构、图搜索和动态规划基础。",
        "goal": "用 Blind 75 题单建立算法面试核心题型覆盖。",
        "defaultDurationWeeks": 4,
        "defaultWeeklyHours": 8,
        "targetAudience": "已有基本编程能力、准备 1 个月左右算法面试冲刺的学习者。",
        "prerequisites": ["能读写一种主力语言", "理解数组、哈希表、链表、树的基本概念"],
        "recommendedFor": ["面试时间较近，需要高频核心题路线", "刷题经验有限但希望快速建立题型地图"],
        "notRecommendedFor": ["完全没有数据结构基础", "希望系统覆盖 150 题以上完整路线的学习者"],
        "expectedOutcome": "完成后能识别主流面试题型，并能独立复盘高频核心题的解法模板和边界条件。",
        "curationNotes": "按 NeetCode Blind 75 标记抽取题目，按 pattern 聚合为 4 个完整执行阶段；生成草稿默认保留所有本地匹配题。",
    },
    "neetcode_150_systematic_interview": {
        "sourceFlag": "neetcode150",
        "title": "NeetCode 150 系统面试计划",
        "summary": "用 12 周覆盖 NeetCode 150 的主流算法面试模式，适合中期系统备战。",
        "goal": "用 NeetCode 150 系统覆盖主流算法面试题型。",
        "defaultDurationWeeks": 12,
        "defaultWeeklyHours": 10,
        "targetAudience": "有 2 到 3 个月准备周期、希望系统覆盖面试算法题型的学习者。",
        "prerequisites": ["掌握一门主力语言的基础语法", "了解常见数据结构和时间复杂度", "每周能稳定投入 8 小时以上"],
        "recommendedFor": ["准备中大型技术面试", "希望按题型模式系统刷题", "刷过少量题但缺少完整路线"],
        "notRecommendedFor": ["只剩 1 到 2 周面试准备时间", "只想做单一薄弱专题突破"],
        "expectedOutcome": "完成后能按题型建立系统化解题策略，并形成覆盖数组、树、图、动态规划和综合题的面试复盘材料。",
        "curationNotes": "按 NeetCode 150 标记抽取题目，沿用 pattern 顺序组织为 12 个完整执行阶段；生成草稿默认保留所有本地匹配题。",
    },
}


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate learning plan template seed from NeetCode data.")
    parser.add_argument("--source-json", default="", help="Local .problemSiteData.json path.")
    parser.add_argument("--source-url", default=SOURCE_RAW_URL)
    parser.add_argument("--local-problems", default=str(DEFAULT_LOCAL_PROBLEMS_PATH))
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    args = parser.parse_args()

    source_rows = load_source_rows(Path(args.source_json) if args.source_json else None, args.source_url)
    local_slugs = load_local_slugs(Path(args.local_problems))
    templates, refs, report = build_seed(source_rows, local_slugs)
    write_seed(Path(args.output_dir), templates, refs, report)
    print({
        "templates": report["templateCount"],
        "problemRefs": report["problemRefCount"],
        "matched": report["matchedProblemCount"],
        "missing": report["missingProblemCount"],
    })


def load_source_rows(source_json: Path | None, source_url: str) -> list[dict[str, Any]]:
    if source_json and source_json.exists():
        return json.loads(source_json.read_text(encoding="utf-8"))
    with urllib.request.urlopen(source_url, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def load_local_slugs(path: Path) -> set[str]:
    if not path.exists():
        return set()
    return {str(row["slug"]) for row in read_jsonl(path) if row.get("slug")}


def build_seed(
    source_rows: list[dict[str, Any]],
    local_slugs: set[str],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, Any]]:
    templates: list[dict[str, Any]] = []
    refs: list[dict[str, Any]] = []
    reports: dict[str, Any] = {}

    for template_id, config in TEMPLATE_CONFIGS.items():
        source_flag = str(config["sourceFlag"])
        route_rows = [row for row in source_rows if row.get(source_flag) is True]
        template_refs = build_refs(template_id, route_rows, local_slugs)
        refs.extend(template_refs)
        template = build_template(template_id, config, route_rows, template_refs)
        validate_template(template)
        templates.append(template)
        reports[template_id] = template_report(template, template_refs)

    validate_seed(templates, refs)
    report = {
        "schemaVersion": 1,
        "generatedAt": utc_now_iso(),
        "source": {
            "name": SOURCE_NAME,
            "url": SOURCE_URL,
            "commit": SOURCE_COMMIT,
            "dataPath": SOURCE_DATA_PATH,
        },
        "templateCount": len(templates),
        "problemRefCount": len(refs),
        "matchedProblemCount": sum(1 for ref in refs if ref["metadata"]["matchedLocalProblem"]),
        "missingProblemCount": sum(1 for ref in refs if not ref["metadata"]["matchedLocalProblem"]),
        "templates": reports,
    }
    return templates, refs, report


def build_refs(
    template_id: str,
    rows: list[dict[str, Any]],
    local_slugs: set[str],
) -> list[dict[str, Any]]:
    phase_by_pattern = pattern_phase_index(template_id)
    sort_order_by_phase: dict[int, int] = defaultdict(int)
    refs: list[dict[str, Any]] = []
    for source_order, row in enumerate(rows, start=1):
        slug = problem_slug(row)
        pattern = clean_text(row.get("pattern"))
        phase_index = phase_by_pattern.get(pattern, len(PHASES[template_id]))
        sort_order_by_phase[phase_index] += 1
        matched = slug in local_slugs
        refs.append({
            "templateId": template_id,
            "phaseIndex": phase_index,
            "sortOrder": sort_order_by_phase[phase_index],
            "sourceOrder": source_order,
            "problemSlug": slug,
            "sourceTitle": clean_text(row.get("problem")),
            "sourceDifficulty": difficulty(row.get("difficulty")),
            "pattern": pattern,
            "sourceUrl": source_problem_url(row),
            "metadata": {
                "neetcodePattern": pattern,
                "blind75": bool(row.get("blind75")),
                "neetcode150": bool(row.get("neetcode150")),
                "code": clean_text(row.get("code")),
                "video": clean_text(row.get("video")),
                "matchedLocalProblem": matched,
            },
        })
    return refs


def build_template(
    template_id: str,
    config: dict[str, Any],
    rows: list[dict[str, Any]],
    refs: list[dict[str, Any]],
) -> dict[str, Any]:
    pattern_counts = Counter(clean_text(row.get("pattern")) for row in rows)
    topic_preferences = list(dict.fromkeys(clean_text(row.get("pattern")) for row in rows))
    return {
        "templateId": template_id,
        "title": config["title"],
        "summary": config["summary"],
        "intent": "INTERVIEW_SPRINT",
        "goal": config["goal"],
        "defaultDurationWeeks": config["defaultDurationWeeks"],
        "level": "INTERMEDIATE",
        "defaultWeeklyHours": config["defaultWeeklyHours"],
        "programmingLanguage": "Java",
        "difficultyPreference": "MEDIUM",
        "interviewOriented": True,
        "topicPreferences": topic_preferences,
        "targetAudience": config["targetAudience"],
        "difficultyMix": difficulty_mix(rows),
        "prerequisites": config["prerequisites"],
        "recommendedFor": config["recommendedFor"],
        "notRecommendedFor": config["notRecommendedFor"],
        "expectedOutcome": config["expectedOutcome"],
        "sourceName": SOURCE_NAME,
        "sourceUrl": SOURCE_URL,
        "sourceCommit": SOURCE_COMMIT,
        "sourceDataPath": SOURCE_DATA_PATH,
        "sourceDescription": (
            "NeetCode 公开仓库的 .problemSiteData.json，包含题名、difficulty、pattern、"
            "Blind 75 / NeetCode 150 标记和题目链接。"
        ),
        "curationNotes": config["curationNotes"],
        "licenseNotice": "来源仓库声明 MIT License；本 seed 只使用题单元数据，不包含题解源码或文章内容。",
        "metadata": {
            "sourceFlag": config["sourceFlag"],
            "sourceProblemCount": len(rows),
            "patternCounts": dict(pattern_counts),
            "matchedProblemCount": sum(1 for ref in refs if ref["metadata"]["matchedLocalProblem"]),
            "missingProblemCount": sum(1 for ref in refs if not ref["metadata"]["matchedLocalProblem"]),
            "missingProblems": [
                ref["problemSlug"] for ref in refs if not ref["metadata"]["matchedLocalProblem"]
            ],
        },
        "phases": build_phases(template_id),
    }


def build_phases(template_id: str) -> list[dict[str, Any]]:
    phases: list[dict[str, Any]] = []
    for index, phase in enumerate(PHASES[template_id], start=1):
        patterns = list(phase["patterns"])
        phases.append({
            "phaseIndex": index,
            "title": str(phase["title"]),
            "durationWeeks": int(phase["durationWeeks"]),
            "focus": str(phase["focus"]),
            "objectives": [
                "完成本阶段核心题型的一轮训练",
                "为每类 pattern 记录至少 1 条可复用解题模板",
            ],
            "recommendedTags": patterns,
            "acceptanceCriteria": [
                "能独立说清本阶段推荐题的主解法和复杂度",
                "能复盘错题中的边界条件和状态定义",
            ],
            "reviewAdvice": "按题型整理错题，优先复盘相同 pattern 下重复出错的边界。",
        })
    return phases


def difficulty_mix(rows: list[dict[str, Any]]) -> dict[str, dict[str, int | float]]:
    counts = Counter(difficulty(row.get("difficulty")) for row in rows)
    total = max(1, len(rows))
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
        "problemCount": len(refs),
        "matchedProblemCount": sum(1 for ref in refs if ref["metadata"]["matchedLocalProblem"]),
        "missingProblemCount": sum(1 for ref in refs if not ref["metadata"]["matchedLocalProblem"]),
        "missingProblemExamples": missing_problem_slugs[:10],
        "difficultyMix": template["difficultyMix"],
        "phaseCount": len(template["phases"]),
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
        for field in ["sourceTitle", "pattern", "sourceUrl"]:
            if not clean_text(ref.get(field)):
                raise ValueError(f"missing problem ref field {field}: {template_id}#{slug}")
        sort_orders_by_template_phase[(template_id, phase_index)].append(sort_order)
        ref_count_by_template[template_id] += 1

    for template_id in templates_by_id:
        if ref_count_by_template[template_id] == 0:
            raise ValueError(f"template has no problem refs: {template_id}")

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


def pattern_phase_index(template_id: str) -> dict[str, int]:
    mapping: dict[str, int] = {}
    for index, phase in enumerate(PHASES[template_id], start=1):
        for pattern in phase["patterns"]:
            mapping[str(pattern)] = index
    return mapping


def problem_slug(row: dict[str, Any]) -> str:
    code = clean_text(row.get("code"))
    match = re.match(r"^\d+-(.+)$", code)
    if match:
        return match.group(1)
    link = clean_text(row.get("link")).strip("/")
    return link.split("/")[-1]


def source_problem_url(row: dict[str, Any]) -> str:
    link = clean_text(row.get("link")).strip("/")
    return f"https://neetcode.io/problems/{link}"


def difficulty(value: Any) -> str:
    text = clean_text(value)
    return text if text in {"Easy", "Medium", "Hard"} else "Medium"


def clean_text(value: Any) -> str:
    return "" if value is None else str(value).strip()


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
    manifest = {
        **report,
        "files": {
            TEMPLATES_FILE: file_stats(output_dir / TEMPLATES_FILE),
            PROBLEM_REFS_FILE: file_stats(output_dir / PROBLEM_REFS_FILE),
            METADATA_FILE: file_stats(output_dir / METADATA_FILE),
        },
    }
    write_json(output_dir / MANIFEST_FILE, manifest)


def metadata_markdown_text(report: dict[str, Any]) -> str:
    lines = [
        "# 学习计划模板 Seed 元数据",
        "",
        f"- 生成时间：`{report['generatedAt']}`",
        f"- 来源：`{SOURCE_NAME}`",
        f"- 固定 commit：`{SOURCE_COMMIT}`",
        f"- 源文件：`{SOURCE_DATA_PATH}`",
        f"- 模板数：`{report['templateCount']}`",
        f"- 题目引用数：`{report['problemRefCount']}`",
        f"- 本地题库匹配：`{report['matchedProblemCount']}`",
        f"- 本地题库缺失：`{report['missingProblemCount']}`",
        "",
        "## 本批模板",
        "",
    ]
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
        "- 本阶段只跑通模板 seed 到草稿的最小闭环，不提供完整模板市场和自动推荐。",
        "- 模板阶段是生成草稿的完整执行路线，草稿默认包含所有本地匹配题。",
        "- 缺失题只进入模板明细、导入审计和草稿 metadata，不进入草稿推荐题。",
        "- 来源授权按仓库当前 LICENSE 记录；后续发布前仍需复核第三方资料源授权边界。",
        "",
    ])
    return "\n".join(lines)


def file_stats(path: Path) -> dict[str, Any]:
    import hashlib

    data = path.read_bytes()
    return {
        "path": str(path),
        "bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
    }


if __name__ == "__main__":
    main()
