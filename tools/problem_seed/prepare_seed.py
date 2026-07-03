#!/usr/bin/env python3
"""从本地 LeetCode 官方 API 缓存生成最终题库 seed 文件。"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path
from typing import Any, Iterable

if __package__ in (None, ""):
    sys.path.append(str(Path(__file__).resolve().parents[2]))

from tools.problem_seed.leetcode_api import (
    CN_SITE,
    COM_SITE,
    SITE_BASE_URLS,
    cache_path,
    non_empty_text,
    question_from_response,
    read_json,
    read_jsonl,
    utc_now_iso,
    write_json,
    write_jsonl,
)


DEFAULT_INDEX_PATH = Path("data/index/problem_index.jsonl")
DEFAULT_CACHE_DIR = Path("data/sources/leetcode-api")
DEFAULT_OUTPUT_DIR = Path("data/seed")
DEFAULT_SOURCE_REPOSITORY = "leetcode-api-cache"
CONTENT_STATUS_BILINGUAL = "BILINGUAL"
CONTENT_STATUS_CN_ONLY = "CN_ONLY"
SOURCE_SITE_LEETCODE_COM_CN = "LEETCODE_COM_CN"
SOURCE_SITE_LEETCODE_CN = "LEETCODE_CN"


@dataclass(frozen=True)
class SeedProblem:
    slug: str
    frontend_id: int | None
    frontend_display_id: str | None
    title_en: str | None
    title_zh: str
    difficulty: str | None
    tag_values: list[str]
    tag_labels_en: list[str]
    tag_labels_zh: list[str]
    content_markdown_en: str | None
    content_markdown_zh: str
    content_status: str
    source_site: str
    leetcode_url: str | None
    sample_test_case: str | None
    python3_template: str | None
    source_commit: str | None


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate final problem seed jsonl files from LeetCode API cache.")
    parser.add_argument("--index", default=str(DEFAULT_INDEX_PATH))
    parser.add_argument("--cache-dir", default=str(DEFAULT_CACHE_DIR))
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    args = parser.parse_args()

    problems = build_seed(Path(args.index), Path(args.cache_dir))
    write_seed(Path(args.output_dir), problems, source_commit(Path(args.cache_dir)))


def build_seed(index_path: Path, cache_dir: Path) -> list[SeedProblem]:
    problems: list[SeedProblem] = []
    commit = source_commit(cache_dir)
    for row in read_jsonl(index_path):
        seed_problem = build_problem(row, cache_dir, commit)
        if seed_problem is not None:
            problems.append(seed_problem)
    return dedupe_by_slug(problems)


def build_problem(row: dict[str, Any], cache_dir: Path, commit: str | None) -> SeedProblem | None:
    slug = as_optional_string(row.get("slug"))
    if slug is None:
        return None
    com_question = question_from_response(read_optional(cache_path(cache_dir, slug, COM_SITE)))
    cn_question = question_from_response(read_optional(cache_path(cache_dir, slug, CN_SITE)))
    has_com_content = non_empty_text(com_question.get("content") if com_question else None)
    has_cn_content = non_empty_text(cn_question.get("translatedContent") if cn_question else None)
    if is_paid_only(row, com_question, cn_question) or not has_cn_content:
        return None
    if has_com_content:
        return bilingual_problem(slug, row, com_question or {}, cn_question or {}, commit)
    return cn_only_problem(slug, row, cn_question or {}, commit)


def bilingual_problem(
    slug: str,
    row: dict[str, Any],
    com_question: dict[str, Any],
    cn_question: dict[str, Any],
    commit: str | None,
) -> SeedProblem | None:
    title_en = as_optional_string(first_non_empty(com_question.get("title"), row.get("titleEn")))
    title_zh = as_optional_string(first_non_empty(
        cn_question.get("translatedTitle"),
        cn_question.get("title"),
        row.get("titleZh"),
        title_en,
    ))
    content_en = as_markdown(title_en, com_question.get("content"))
    content_zh = as_markdown(title_zh, cn_question.get("translatedContent"))
    if not title_en or not title_zh or not content_en or not content_zh:
        return None
    tag_values, tag_labels_en, tag_labels_zh = merged_tag_arrays(com_question, cn_question)
    return SeedProblem(
        slug=slug,
        frontend_id=read_frontend_id(row, com_question, cn_question),
        frontend_display_id=read_frontend_display_id(row, com_question, cn_question),
        title_en=title_en,
        title_zh=title_zh,
        difficulty=read_difficulty(first_mapping(com_question, cn_question, row)),
        tag_values=tag_values,
        tag_labels_en=tag_labels_en,
        tag_labels_zh=tag_labels_zh,
        content_markdown_en=content_en,
        content_markdown_zh=content_zh,
        content_status=CONTENT_STATUS_BILINGUAL,
        source_site=SOURCE_SITE_LEETCODE_COM_CN,
        leetcode_url=leetcode_url(COM_SITE, slug),
        sample_test_case=read_sample_test_case(com_question, cn_question),
        python3_template=read_python3_template(com_question) or read_python3_template(cn_question),
        source_commit=commit,
    )


def cn_only_problem(
    slug: str,
    row: dict[str, Any],
    cn_question: dict[str, Any],
    commit: str | None,
) -> SeedProblem | None:
    title_zh = as_optional_string(first_non_empty(
        cn_question.get("translatedTitle"),
        cn_question.get("title"),
        row.get("titleZh"),
    ))
    content_zh = as_markdown(title_zh, cn_question.get("translatedContent"))
    if not title_zh or not content_zh:
        return None
    tag_values, tag_labels_en, tag_labels_zh = read_tags(cn_question)
    return SeedProblem(
        slug=slug,
        frontend_id=read_frontend_id(row, cn_question),
        frontend_display_id=read_frontend_display_id(row, cn_question),
        title_en=None,
        title_zh=title_zh,
        difficulty=read_difficulty(first_mapping(cn_question, row)),
        tag_values=tag_values,
        tag_labels_en=tag_labels_en,
        tag_labels_zh=tag_labels_zh,
        content_markdown_en=None,
        content_markdown_zh=content_zh,
        content_status=CONTENT_STATUS_CN_ONLY,
        source_site=SOURCE_SITE_LEETCODE_CN,
        leetcode_url=leetcode_url(CN_SITE, slug),
        sample_test_case=read_sample_test_case(cn_question),
        python3_template=read_python3_template(cn_question),
        source_commit=commit,
    )


def write_seed(output_dir: Path, problems: list[SeedProblem], commit: str | None) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    write_jsonl(output_dir / "problems.jsonl", [problem_to_dict(problem) for problem in problems])
    write_jsonl(output_dir / "problem_categories.jsonl", [])
    write_jsonl(output_dir / "problem_category_items.jsonl", [])
    manifest = {
        "sourceRepository": DEFAULT_SOURCE_REPOSITORY,
        "sourceCommit": commit,
        "problemCount": len(problems),
        "contentStatusCounts": content_status_counts(problems),
        "generatedAt": utc_now_iso(),
        "files": [
            "problems.jsonl",
            "problem_categories.jsonl",
            "problem_category_items.jsonl",
        ],
    }
    write_json(output_dir / "manifest.json", manifest)


def problem_to_dict(problem: SeedProblem) -> dict[str, Any]:
    return {
        "slug": problem.slug,
        "frontendId": problem.frontend_id,
        "frontendDisplayId": problem.frontend_display_id,
        "titleEn": problem.title_en,
        "titleZh": problem.title_zh,
        "difficulty": problem.difficulty,
        "tagValues": problem.tag_values,
        "tagLabelsEn": problem.tag_labels_en,
        "tagLabelsZh": problem.tag_labels_zh,
        "contentMarkdownEn": problem.content_markdown_en,
        "contentMarkdownZh": problem.content_markdown_zh,
        "contentStatus": problem.content_status,
        "sourceSite": problem.source_site,
        "leetcodeUrl": problem.leetcode_url,
        "sampleTestCase": problem.sample_test_case,
        "python3Template": problem.python3_template,
        "sourceCommit": problem.source_commit,
    }


def merged_tag_arrays(com_question: dict[str, Any], cn_question: dict[str, Any]) -> tuple[list[str], list[str], list[str]]:
    tag_values, tag_labels_en, fallback_zh = read_tags(com_question)
    cn_tags = cn_question.get("topicTags")
    zh_by_slug: dict[str, str] = {}
    if isinstance(cn_tags, list):
        for tag in cn_tags:
            if isinstance(tag, dict) and tag.get("slug"):
                zh_by_slug[str(tag["slug"])] = str(first_non_empty(
                    tag.get("translatedName"),
                    tag.get("name"),
                    tag.get("slug"),
                ))
    tag_labels_zh = [zh_by_slug.get(value, fallback_zh[index]) for index, value in enumerate(tag_values)]
    return tag_values, tag_labels_en, tag_labels_zh


def read_tags(metadata: dict[str, Any]) -> tuple[list[str], list[str], list[str]]:
    raw = first_non_empty(metadata.get("topicTags"), metadata.get("topic_tags"), metadata.get("tags"))
    if not isinstance(raw, list):
        return [], [], []

    tags: dict[str, tuple[str, str]] = {}
    for item in raw:
        if isinstance(item, dict):
            slug = as_optional_string(first_non_empty(item.get("slug"), item.get("value"), item.get("name")))
            label_en = as_optional_string(first_non_empty(item.get("name"), slug))
            label_zh = as_optional_string(first_non_empty(
                item.get("translatedName"),
                item.get("translated_name"),
                label_en,
            ))
        else:
            label_en = as_optional_string(item)
            label_zh = label_en
            slug = normalize_slug(label_en or "")
        if slug and label_en:
            tags.setdefault(slug, (label_en, label_zh or label_en))

    values = sorted(tags)
    return values, [tags[value][0] for value in values], [tags[value][1] for value in values]


def read_frontend_id(*items: dict[str, Any]) -> int | None:
    raw = read_frontend_display_id(*items)
    if raw is None or not re.fullmatch(r"\d+", raw):
        return None
    return int(raw)


def read_frontend_display_id(*items: dict[str, Any]) -> str | None:
    raw = first_non_empty(*[
        first_non_empty(
            item.get("frontendId"),
            item.get("questionFrontendId"),
            item.get("frontend_question_id"),
            item.get("questionId"),
            item.get("question_id"),
        )
        for item in items
    ])
    return as_optional_string(raw)


def read_difficulty(metadata: dict[str, Any]) -> str | None:
    raw = first_non_empty(metadata.get("difficulty"), metadata.get("level"))
    if isinstance(raw, int):
        return {1: "EASY", 2: "MEDIUM", 3: "HARD"}.get(raw)
    if raw is None:
        return None

    value = str(raw).strip().upper()
    return {
        "EASY": "EASY",
        "MEDIUM": "MEDIUM",
        "HARD": "HARD",
        "简单": "EASY",
        "中等": "MEDIUM",
        "困难": "HARD",
    }.get(value, value)


def read_sample_test_case(*questions: dict[str, Any]) -> str | None:
    for question in questions:
        value = as_optional_string(first_non_empty(question.get("sampleTestCase"), question.get("exampleTestcases")))
        if value:
            return value
    return None


def read_python3_template(metadata: dict[str, Any]) -> str | None:
    snippets = metadata.get("codeSnippets") or metadata.get("code_snippets") or []
    if not isinstance(snippets, list):
        return None

    for snippet in snippets:
        if not isinstance(snippet, dict):
            continue
        lang_slug = str(snippet.get("langSlug") or snippet.get("lang_slug") or "").lower()
        lang = str(snippet.get("lang") or "").lower()
        if lang_slug == "python3" or lang == "python3":
            return as_optional_string(snippet.get("code"))
    return None


def as_markdown(title: str | None, html: Any) -> str | None:
    content = as_optional_string(html)
    if not title or not content:
        return None
    return markdown_with_title(title, html_to_markdown(content))


def markdown_with_title(title: str, body: str) -> str:
    return f"# {title.strip()}\n\n{strip_heading(body).strip()}".strip()


def strip_heading(markdown: str) -> str:
    lines = markdown.strip().splitlines()
    if lines and lines[0].startswith("# "):
        return "\n".join(lines[1:]).strip()
    return markdown.strip()


def leetcode_url(site: str, slug: str) -> str:
    return f"{SITE_BASE_URLS[site]}/problems/{slug}/"


def is_paid_only(row: dict[str, Any], *questions: dict[str, Any] | None) -> bool:
    if bool(row.get("paidOnly")):
        return True
    return any(bool(question and question.get("isPaidOnly")) for question in questions)


def read_optional(path: Path) -> Any | None:
    return read_json(path) if path.exists() else None


def source_commit(cache_dir: Path) -> str | None:
    for directory in (cache_dir, Path.cwd()):
        try:
            result = subprocess.run(
                ["git", "-C", str(directory), "rev-parse", "HEAD"],
                check=True,
                capture_output=True,
                text=True,
            )
            commit = result.stdout.strip()
            if commit:
                return f"leetcode-api@{commit}"
        except (FileNotFoundError, subprocess.CalledProcessError):
            continue
    return None


def dedupe_by_slug(problems: list[SeedProblem]) -> list[SeedProblem]:
    deduped: dict[str, SeedProblem] = {}
    for problem in problems:
        deduped.setdefault(problem.slug, problem)
    return list(deduped.values())


def content_status_counts(problems: Iterable[SeedProblem]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for problem in problems:
        counts[problem.content_status] = counts.get(problem.content_status, 0) + 1
    return dict(sorted(counts.items()))


class SimpleHtmlMarkdownParser(HTMLParser):

    BLOCK_TAGS = {"div", "p", "section", "article", "blockquote"}

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []
        self.in_pre = False
        self.in_inline_code = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in self.BLOCK_TAGS:
            self.ensure_blank_line()
        elif tag == "br":
            self.write("\n")
        elif tag == "pre":
            self.ensure_blank_line()
            self.write("```text\n")
            self.in_pre = True
        elif tag == "code" and not self.in_pre:
            self.write("`")
            self.in_inline_code = True
        elif tag in {"strong", "b"} and not self.in_pre:
            self.write("**")
        elif tag in {"em", "i"} and not self.in_pre:
            self.write("_")
        elif tag in {"ul", "ol"}:
            self.ensure_blank_line()
        elif tag == "li":
            self.ensure_line_start()
            self.write("- ")
        elif tag == "sup":
            self.write("<sup>")

    def handle_endtag(self, tag: str) -> None:
        if tag in self.BLOCK_TAGS:
            self.ensure_blank_line()
        elif tag == "pre":
            self.in_pre = False
            self.write("\n```\n")
            self.ensure_blank_line()
        elif tag == "code" and self.in_inline_code and not self.in_pre:
            self.write("`")
            self.in_inline_code = False
        elif tag in {"strong", "b"} and not self.in_pre:
            self.write("**")
        elif tag in {"em", "i"} and not self.in_pre:
            self.write("_")
        elif tag in {"ul", "ol"}:
            self.ensure_blank_line()
        elif tag == "li":
            self.write("\n")
        elif tag == "sup":
            self.write("</sup>")

    def handle_data(self, data: str) -> None:
        if not data:
            return
        text = data.replace("\xa0", " ")
        if not self.in_pre:
            text = re.sub(r"[ \t\r\n]+", " ", text)
        self.write(text)

    def markdown(self) -> str:
        text = "".join(self.parts)
        text = re.sub(r"[ \t]+\n", "\n", text)
        text = re.sub(r"\n{3,}", "\n\n", text)
        return text.strip()

    def write(self, text: str) -> None:
        self.parts.append(text)

    def ensure_blank_line(self) -> None:
        current = "".join(self.parts)
        if not current:
            return
        if current.endswith("\n\n"):
            return
        if current.endswith("\n"):
            self.write("\n")
        else:
            self.write("\n\n")

    def ensure_line_start(self) -> None:
        current = "".join(self.parts)
        if current and not current.endswith("\n"):
            self.write("\n")


def html_to_markdown(html: str) -> str:
    parser = SimpleHtmlMarkdownParser()
    parser.feed(html)
    parser.close()
    return parser.markdown()


def normalize_slug(value: str) -> str | None:
    slug = re.sub(r"[^a-z0-9]+", "-", value.strip().lower()).strip("-")
    return slug or None


def first_mapping(*items: dict[str, Any]) -> dict[str, Any]:
    for item in items:
        if item:
            return item
    return {}


def nested(raw: dict[str, Any], *keys: str) -> Any:
    value: Any = raw
    for key in keys:
        if not isinstance(value, dict):
            return None
        value = value.get(key)
    return value


def first_non_empty(*values: Any) -> Any:
    for value in values:
        if value is None:
            continue
        if isinstance(value, str) and not value.strip():
            continue
        return value
    return None


def as_optional_string(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


if __name__ == "__main__":
    main()
