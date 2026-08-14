"""LeetCode 题目学习元数据管线的固定数据契约。"""

from __future__ import annotations

from typing import Final


METADATA_QUERY_VERSION: Final = "leetcode-question-learning-metadata.v1"
METADATA_GRAPHQL_OPERATION: Final = "questionLearningMetadata"
METADATA_GRAPHQL_QUERY: Final = (
    "query questionLearningMetadata($titleSlug: String!) { "
    "question(titleSlug: $titleSlug) { "
    "titleSlug categoryTitle hints similarQuestions "
    "codeSnippets { lang langSlug code } "
    "} "
    "}"
)

METADATA_SOURCE: Final = "LEETCODE"
RELATION_TYPE_LEETCODE_SIMILAR: Final = "LEETCODE_SIMILAR"
SOURCE_SITE_COM: Final = "LEETCODE_COM"
SOURCE_SITE_CN: Final = "LEETCODE_CN"
SITE_TO_SOURCE_SITE: Final = {"com": SOURCE_SITE_COM, "cn": SOURCE_SITE_CN}

RELATIONS_FILE: Final = "problem_relations.jsonl"
HINTS_FILE: Final = "problem_hints.jsonl"
CODE_TEMPLATES_FILE: Final = "problem_code_templates.jsonl"
CATEGORIES_FILE: Final = "problem_categories.jsonl"
CATEGORY_ITEMS_FILE: Final = "problem_category_items.jsonl"
MANIFEST_FILE: Final = "problem_metadata_seed_manifest.json"
AUDIT_REPORT_FILE: Final = "problem_metadata_audit_report.json"
FETCH_REPORT_FILE: Final = "problem_metadata_fetch_report.json"

OUTPUT_FILES: Final = (
    RELATIONS_FILE,
    HINTS_FILE,
    CODE_TEMPLATES_FILE,
    CATEGORIES_FILE,
    CATEGORY_ITEMS_FILE,
)

# categoryTitle 是来源展示文案，必须在此受控目录中映射成稳定的本地分类契约。
CATEGORY_TITLE_MAP: Final = {
    "Algorithms": {"slug": "algorithms", "nameEn": "Algorithms", "nameZh": "算法"},
    "Database": {"slug": "database", "nameEn": "Database", "nameZh": "数据库"},
    "Shell": {"slug": "shell", "nameEn": "Shell", "nameZh": "Shell"},
    "Concurrency": {"slug": "concurrency", "nameEn": "Concurrency", "nameZh": "并发"},
    "JavaScript": {"slug": "javascript", "nameEn": "JavaScript", "nameZh": "JavaScript"},
    "LCCI": {"slug": "lcci", "nameEn": "LCCI", "nameZh": "程序员面试经典"},
    "pandas": {"slug": "pandas", "nameEn": "pandas", "nameZh": "pandas"},
}

JSON_FIELD_SOURCE_SNAPSHOT: Final = "sourceSnapshot"
JSON_FIELD_OUTCOME: Final = "outcome"
AUDIT_OUTCOME_PASSED: Final = "PASSED"
