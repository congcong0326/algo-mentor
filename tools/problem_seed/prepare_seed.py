#!/usr/bin/env python3
"""从本地 leetcode-problemset 源目录生成题库 seed 文件。"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from dataclasses import dataclass
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path
from typing import Any, Iterable


SOLUTION_HEADING_PATTERN = re.compile(r"(?im)^##\s*solution\b.*$")
DEFAULT_SOURCE_REPOSITORY = "fishjar/leetcode-problemset"


@dataclass(frozen=True)
class SeedProblem:
    slug: str
    frontend_id: int | None
    title_en: str
    title_zh: str
    difficulty: str | None
    tag_values: list[str]
    tag_labels_en: list[str]
    tag_labels_zh: list[str]
    content_markdown_en: str
    content_markdown_zh: str
    leetcode_url: str | None
    sample_test_case: str | None
    python3_template: str | None
    source_commit: str | None


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate problem seed jsonl files.")
    parser.add_argument("--source-dir", default="data/sources/leetcode-problemset")
    parser.add_argument("--output-dir", default="data/seed")
    args = parser.parse_args()

    source_dir = Path(args.source_dir)
    output_dir = Path(args.output_dir)
    problems = build_seed(source_dir)
    write_seed(output_dir, problems, source_commit(source_dir))


def build_seed(source_dir: Path) -> list[SeedProblem]:
    metadata_by_key = load_problem_metadata(source_dir)
    markdown_dir = source_dir / "problemset_md"
    commit = source_commit(source_dir)
    problems: list[SeedProblem] = []

    if not markdown_dir.exists():
      raise FileNotFoundError(f"Markdown source directory does not exist: {markdown_dir}")

    for markdown_file in sorted(markdown_dir.rglob("*.md")):
        markdown = markdown_file.read_text(encoding="utf-8")
        problem_key = problem_key_from_path(markdown_file)
        metadata = metadata_by_key.get(problem_key, {})
        seed_problem = parse_problem(markdown_file, markdown, metadata, commit)
        if seed_problem is not None:
            problems.append(seed_problem)

    return dedupe_by_slug(problems)


def load_problem_metadata(source_dir: Path) -> dict[str, dict[str, Any]]:
    if not source_dir.exists():
        return {}

    metadata: dict[str, dict[str, Any]] = {}
    for path in sorted(source_dir.rglob("*.json")):
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, UnicodeDecodeError):
            continue

        for item in flatten_problem_records(raw):
            keys = metadata_keys(item, path)
            for key in keys:
                existing = metadata.get(key)
                if existing is None or metadata_score(item) > metadata_score(existing):
                    metadata[key] = item

    return metadata


def flatten_problem_records(raw: Any) -> Iterable[dict[str, Any]]:
    if isinstance(raw, dict):
        if isinstance(raw.get("question"), dict):
            yield raw["question"]
            return
        if "stat" in raw or "questionFrontendId" in raw or "titleSlug" in raw:
            yield raw
            return
        for value in raw.values():
            yield from flatten_problem_records(value)
    elif isinstance(raw, list):
        for value in raw:
            yield from flatten_problem_records(value)


def metadata_keys(item: dict[str, Any], path: Path) -> set[str]:
    stat = item.get("stat") if isinstance(item.get("stat"), dict) else {}
    frontend_id = first_non_empty(
        item.get("frontend_question_id"),
        item.get("frontendId"),
        item.get("questionFrontendId"),
        stat.get("frontend_question_id"),
        stat.get("question_id"),
    )
    slug = first_non_empty(
        item.get("titleSlug"),
        item.get("title_slug"),
        item.get("slug"),
        stat.get("question__title_slug"),
    )

    keys = {path.stem}
    if frontend_id is not None:
        keys.add(str(frontend_id))
    if slug:
        keys.add(str(slug))
    return keys


def parse_problem(
    markdown_file: Path,
    markdown: str,
    metadata: dict[str, Any],
    commit: str | None,
) -> SeedProblem | None:
    fallback_markdown = strip_solution(markdown).strip()
    title_en = read_title(fallback_markdown, metadata)
    title_zh = read_title_zh(metadata, title_en)
    slug = read_slug(markdown_file, metadata, title_en)
    content_markdown_en = read_content_markdown_en(fallback_markdown, metadata, title_en)
    content_markdown_zh = read_content_markdown_zh(fallback_markdown, metadata, title_zh, title_en)
    tag_values, tag_labels_en, tag_labels_zh = read_tags(metadata)
    if not slug or not title_en or not title_zh or not content_markdown_en or not content_markdown_zh:
        return None

    return SeedProblem(
        slug=slug,
        frontend_id=read_frontend_id(markdown_file, metadata),
        title_en=title_en,
        title_zh=title_zh,
        difficulty=read_difficulty(metadata),
        tag_values=tag_values,
        tag_labels_en=tag_labels_en,
        tag_labels_zh=tag_labels_zh,
        content_markdown_en=content_markdown_en,
        content_markdown_zh=content_markdown_zh,
        leetcode_url=read_leetcode_url(slug, metadata),
        sample_test_case=as_optional_string(metadata.get("sampleTestCase") or metadata.get("sample_test_case")),
        python3_template=read_python3_template(metadata),
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
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "files": [
            "problems.jsonl",
            "problem_categories.jsonl",
            "problem_category_items.jsonl",
        ],
    }
    (output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8") as file:
        for row in rows:
            file.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")


def problem_to_dict(problem: SeedProblem) -> dict[str, Any]:
    return {
        "slug": problem.slug,
        "frontendId": problem.frontend_id,
        "titleEn": problem.title_en,
        "titleZh": problem.title_zh,
        "difficulty": problem.difficulty,
        "tagValues": problem.tag_values,
        "tagLabelsEn": problem.tag_labels_en,
        "tagLabelsZh": problem.tag_labels_zh,
        "contentMarkdownEn": problem.content_markdown_en,
        "contentMarkdownZh": problem.content_markdown_zh,
        "leetcodeUrl": problem.leetcode_url,
        "sampleTestCase": problem.sample_test_case,
        "python3Template": problem.python3_template,
        "sourceCommit": problem.source_commit,
    }


def strip_solution(markdown: str) -> str:
    match = SOLUTION_HEADING_PATTERN.search(markdown)
    if not match:
        return markdown
    return markdown[:match.start()]


def read_title(markdown: str, metadata: dict[str, Any]) -> str | None:
    title = first_non_empty(
        metadata.get("title"),
        metadata.get("questionTitle"),
        nested(metadata, "stat", "question__title"),
    )
    if title:
        return str(title).strip()

    for line in markdown.splitlines():
        if line.startswith("# "):
            return line[2:].strip()
    return None


def read_slug(markdown_file: Path, metadata: dict[str, Any], title: str | None) -> str | None:
    slug = first_non_empty(
        metadata.get("titleSlug"),
        metadata.get("title_slug"),
        metadata.get("slug"),
        nested(metadata, "stat", "question__title_slug"),
    )
    if slug:
        return normalize_slug(str(slug))

    stem = markdown_file.stem
    if re.fullmatch(r"\d+", stem) and title:
        return normalize_slug(title)
    return normalize_slug(stem)


def read_frontend_id(markdown_file: Path, metadata: dict[str, Any]) -> int | None:
    raw = first_non_empty(
        metadata.get("frontend_question_id"),
        metadata.get("frontendId"),
        metadata.get("questionFrontendId"),
        nested(metadata, "stat", "frontend_question_id"),
        nested(metadata, "stat", "question_id"),
        markdown_file.stem if re.fullmatch(r"\d+", markdown_file.stem) else None,
    )
    try:
        return int(str(raw))
    except (TypeError, ValueError):
        return None


def read_title_zh(metadata: dict[str, Any], title_en: str | None) -> str | None:
    return as_optional_string(first_non_empty(
        metadata.get("translatedTitle"),
        metadata.get("translated_title"),
        metadata.get("titleCn"),
        metadata.get("title_cn"),
        title_en,
    ))


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


def read_content_markdown_en(markdown: str, metadata: dict[str, Any], title_en: str | None) -> str:
    content = as_optional_string(first_non_empty(metadata.get("content"), metadata.get("content_en")))
    if content and title_en:
        return markdown_with_title(title_en, html_to_markdown(content))
    english, _ = split_markdown_translation(markdown)
    return english


def read_content_markdown_zh(
    markdown: str,
    metadata: dict[str, Any],
    title_zh: str | None,
    title_en: str | None,
) -> str:
    content = as_optional_string(first_non_empty(
        metadata.get("translatedContent"),
        metadata.get("translated_content"),
        metadata.get("content_zh"),
    ))
    if content and title_zh:
        return markdown_with_title(title_zh, html_to_markdown(content))
    _, translated = split_markdown_translation(markdown)
    if translated and title_zh:
        return markdown_with_title(title_zh, translated)
    return markdown_with_title(title_zh or title_en or "", strip_heading(markdown))


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


def read_leetcode_url(slug: str, metadata: dict[str, Any]) -> str | None:
    raw = as_optional_string(first_non_empty(metadata.get("leetcodeUrl"), metadata.get("url")))
    if raw:
        return raw
    return f"https://leetcode.com/problems/{slug}/"


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


def source_commit(source_dir: Path) -> str | None:
    try:
        result = subprocess.run(
            ["git", "-C", str(source_dir), "rev-parse", "HEAD"],
            check=True,
            capture_output=True,
            text=True,
        )
        return result.stdout.strip() or None
    except (FileNotFoundError, subprocess.CalledProcessError):
        return None


def problem_key_from_path(markdown_file: Path) -> str:
    return markdown_file.stem


def dedupe_by_slug(problems: list[SeedProblem]) -> list[SeedProblem]:
    deduped: dict[str, SeedProblem] = {}
    for problem in problems:
        deduped.setdefault(problem.slug, problem)
    return list(deduped.values())


def metadata_score(item: dict[str, Any]) -> int:
    score = 0
    for key in ("content", "translatedContent", "codeSnippets", "topicTags", "sampleTestCase"):
        if item.get(key):
            score += 1
    return score


def split_markdown_translation(markdown: str) -> tuple[str, str | None]:
    match = re.search(r"(?im)^##\s*翻译\b.*$", markdown)
    if not match:
        return markdown.strip(), None
    english = markdown[:match.start()].strip()
    translated = markdown[match.end():].strip()
    return english, translated or None


def strip_heading(markdown: str) -> str:
    lines = markdown.strip().splitlines()
    if lines and lines[0].startswith("# "):
        return "\n".join(lines[1:]).strip()
    return markdown.strip()


def markdown_with_title(title: str, body: str) -> str:
    return f"# {title.strip()}\n\n{strip_heading(body).strip()}".strip()


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
    text = str(value)
    return text if text else None


if __name__ == "__main__":
    main()
