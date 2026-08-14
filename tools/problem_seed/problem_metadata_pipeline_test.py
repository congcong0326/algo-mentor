import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools.problem_seed import fetch_problem_metadata, prepare_problem_metadata_seed, validate_problem_metadata_seed
from tools.problem_seed.problem_metadata_contract import FETCH_REPORT_FILE


def response(question: dict | None) -> dict:
    return {"data": {"question": question}}


def question(slug: str, **overrides: object) -> dict:
    value = {
        "titleSlug": slug,
        "categoryTitle": "Algorithms",
        "hints": ["<p>Try a <code>hash map</code>.</p>"],
        "similarQuestions": json.dumps([{
            "titleSlug": "outside-local-catalog",
            "title": "Outside",
            "difficulty": "Medium",
            "paidOnly": False,
        }]),
        "codeSnippets": [
            {"lang": "Python3", "langSlug": "python3", "code": "class Solution:\n    pass"},
            {"lang": "Java", "langSlug": "java", "code": "class Solution {}"},
        ],
    }
    value.update(overrides)
    return value


class ProblemMetadataPipelineTest(unittest.TestCase):

    @mock.patch("tools.problem_seed.fetch_problem_metadata.request_json")
    def test_fetch_records_success_not_available_and_failure_terminals(self, request_json: mock.Mock) -> None:
        request_json.side_effect = [
            mock.Mock(status=200, body=response(question("two-sum")), error=None),
            mock.Mock(status=404, body=None, error="HTTP 404"),
            mock.Mock(status=0, body=None, error="timeout"),
        ]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            seed = root / "problems.jsonl"
            index = root / "index.jsonl"
            cache = root / "cache"
            seed.write_text(
                "\n".join(json.dumps({"slug": slug, "sourceSite": "LEETCODE_COM_CN"}) for slug in ("two-sum", "gone", "broken")) + "\n",
                encoding="utf-8")
            index.write_text(
                "\n".join(json.dumps({"slug": slug, "onCom": True, "onCn": False}) for slug in ("two-sum", "gone", "broken")) + "\n",
                encoding="utf-8")

            report = fetch_problem_metadata.fetch_metadata(seed, index, cache, retries=2, sleeper=lambda _: None)

            self.assertEqual(1, report["summary"]["fetchedCount"])
            self.assertEqual(1, report["summary"]["notAvailableCount"])
            self.assertEqual(1, report["summary"]["failedFetchCount"])
            self.assertTrue((cache / "broken.com.json").exists())
            self.assertFalse((cache / "gone.com.json").exists())
            self.assertEqual("questionLearningMetadata", request_json.call_args_list[0].kwargs["payload"]["operationName"])
            self.assertEqual(2, request_json.call_args_list[0].kwargs["retries"])

    def test_select_source_rows_filters_slug_before_limit(self) -> None:
        selected = fetch_problem_metadata.select_source_rows(
            [{"slug": slug} for slug in ("a", "b", "c")],
            [{"slug": slug, "onCom": True, "onCn": False} for slug in ("a", "b", "c")],
            ["b", "c"],
            1,
        )

        self.assertEqual([{"slug": "b", "sites": ["com"]}], selected)

    @mock.patch("tools.problem_seed.fetch_problem_metadata.request_json")
    def test_fetch_reuses_existing_cache_for_selected_slug(self, request_json: mock.Mock) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            seed = root / "problems.jsonl"
            index = root / "index.jsonl"
            cache = root / "cache"
            seed.write_text(
                "\n".join(json.dumps({"slug": slug}) for slug in ("two-sum", "other")) + "\n",
                encoding="utf-8")
            index.write_text(
                "\n".join(json.dumps({"slug": slug, "onCom": True, "onCn": False}) for slug in ("two-sum", "other")) + "\n",
                encoding="utf-8")
            cache.mkdir()
            (cache / "two-sum.com.json").write_text(
                json.dumps(response(question("two-sum"))), encoding="utf-8")

            report = fetch_problem_metadata.fetch_metadata(
                seed, index, cache, slugs=["two-sum"], limit=1, sleeper=lambda _: None)

            request_json.assert_not_called()
            self.assertEqual(1, report["summary"]["sourceProblemCount"])
            self.assertEqual({"fetched": 1}, report["summary"]["sourceStatusCounts"])

    def test_build_seed_merges_sites_and_preserves_com_template_priority(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            seed, cache = self.write_fixture(root)
            result = prepare_problem_metadata_seed.build_seed(seed, cache)

            self.assertEqual([], result["errors"])
            relation = result["outputRows"]["problem_relations.jsonl"][0]
            self.assertEqual("outside-local-catalog", relation["targetSlug"])
            self.assertEqual("外部题", relation["metadata"]["translatedTitle"])
            self.assertEqual(["LEETCODE_CN", "LEETCODE_COM"], relation["metadata"]["sourceSites"])
            templates = result["outputRows"]["problem_code_templates.jsonl"]
            self.assertEqual("class Solution {}", next(row["code"] for row in templates if row["languageSlug"] == "java"))
            self.assertEqual("LEETCODE_COM", next(row["sourceSite"] for row in templates if row["languageSlug"] == "java"))
            self.assertEqual("Try a `hash map`.", result["outputRows"]["problem_hints.jsonl"][0]["contentMarkdown"])

    @mock.patch("tools.problem_seed.fetch_problem_metadata.request_json")
    def test_site_filter_refills_only_requested_cache_without_overwriting_report(self, request_json: mock.Mock) -> None:
        request_json.return_value = mock.Mock(status=200, body=response(question("two-sum")), error=None)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            seed = root / "problems.jsonl"
            index = root / "index.jsonl"
            cache = root / "cache"
            seed.write_text(json.dumps({"slug": "two-sum", "sourceSite": "LEETCODE_COM_CN"}) + "\n", encoding="utf-8")
            index.write_text(json.dumps({"slug": "two-sum", "onCom": True, "onCn": True}) + "\n", encoding="utf-8")

            fetch_problem_metadata.fetch_metadata(
                seed, index, cache, selected_sites={"com"}, write_report=False, sleeper=lambda _: None)

            self.assertTrue((cache / "two-sum.com.json").exists())
            self.assertFalse((cache / "two-sum.cn.json").exists())
            self.assertFalse((cache / FETCH_REPORT_FILE).exists())

    def test_build_seed_reports_unknown_category_and_duplicate_language(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            seed, cache = self.write_fixture(root, category="Unknown", duplicate_java=True)
            result = prepare_problem_metadata_seed.build_seed(seed, cache)

            self.assertEqual({"duplicate_code_language_on_site", "unknown_category_title"}, {error["rule"] for error in result["errors"]})

    def test_build_seed_trims_templates_and_excludes_upstream_self_reference(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            seed, cache = self.write_fixture(root)
            com_path = cache / "two-sum.com.json"
            payload = json.loads(com_path.read_text(encoding="utf-8"))
            payload["data"]["question"]["similarQuestions"] = json.dumps([
                {"titleSlug": "two-sum", "title": "Two Sum", "difficulty": "EASY", "paidOnly": False},
                {"titleSlug": "outside-local-catalog", "title": "Outside", "difficulty": "MEDIUM", "paidOnly": False},
            ])
            payload["data"]["question"]["codeSnippets"][0]["code"] += "\n        "
            com_path.write_text(json.dumps(payload), encoding="utf-8")

            result = prepare_problem_metadata_seed.build_seed(seed, cache)

            self.assertEqual([], result["errors"])
            self.assertEqual(1, result["stats"]["excludedSelfReferenceCount"])
            self.assertEqual("class Solution:\n    pass", next(
                row["code"] for row in result["outputRows"]["problem_code_templates.jsonl"]
                if row["languageSlug"] == "python3"))

    def test_validator_rejects_tampered_output_and_requires_manual_sample(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            seed, cache = self.write_fixture(root)
            output = root / "output"
            prepare_problem_metadata_seed.write_seed(output, prepare_problem_metadata_seed.build_seed(seed, cache))
            pending = validate_problem_metadata_seed.build_audit_report(output, seed)
            self.assertEqual("FAILED", pending["outcome"])
            passed = validate_problem_metadata_seed.build_audit_report(
                output, seed, manual_sample_size=20, manual_conclusion="20 个样本均符合来源快照。")
            self.assertEqual("PASSED", passed["outcome"])
            (output / "problem_metadata_audit_report.json").write_text(
                json.dumps(passed, ensure_ascii=False), encoding="utf-8")
            self.assertEqual("PASSED", validate_problem_metadata_seed.build_audit_report(output, seed)["outcome"])
            (output / "problem_hints.jsonl").write_text("{}\n", encoding="utf-8")
            tampered = validate_problem_metadata_seed.build_audit_report(
                output, seed, manual_sample_size=20, manual_conclusion="tampered")
            self.assertEqual("FAILED", tampered["outcome"])
            self.assertIn("output_checksum_mismatch", {error["rule"] for error in tampered["errors"]})

    def write_fixture(self, root: Path, category: str = "Algorithms", duplicate_java: bool = False) -> tuple[Path, Path]:
        seed = root / "problems.jsonl"
        cache = root / "cache"
        cache.mkdir()
        seed.write_text(json.dumps({
            "slug": "two-sum",
            "sourceSite": "LEETCODE_COM_CN",
            "python3Template": "class Solution:\n    pass",
        }) + "\n", encoding="utf-8")
        com = question("two-sum", categoryTitle=category)
        if duplicate_java:
            com["codeSnippets"].append({"lang": "Java 21", "langSlug": "java", "code": "duplicate"})
        cn = question("two-sum", hints=[], similarQuestions=json.dumps([{
            "titleSlug": "outside-local-catalog",
            "title": "Outside CN",
            "translatedTitle": "外部题",
            "difficulty": "MEDIUM",
            "paidOnly": False,
        }]), codeSnippets=[{"lang": "Java", "langSlug": "java", "code": "cn fallback"}])
        (cache / "two-sum.com.json").write_text(json.dumps(response(com)), encoding="utf-8")
        (cache / "two-sum.cn.json").write_text(json.dumps(response(cn)), encoding="utf-8")
        fetch_report = {
            "queryVersion": "leetcode-question-learning-metadata.v1",
            "inputProblemsSha256": prepare_problem_metadata_seed.sha256_file(seed),
            "sourceSnapshot": "leetcode-question-learning-metadata.v1@test",
            "completedAt": "2026-08-14T00:00:00+00:00",
            "sourceStates": [{"slug": "two-sum", "status": "fetched", "sites": ["com", "cn"]}],
            "cacheChecksums": {},
            "summary": {
                "sourceProblemCount": 1,
                "sourceStatusCounts": {"fetched": 1},
                "failedFetchCount": 0,
                "notAvailableCount": 0,
                "fetchedCount": 1,
            },
        }
        (cache / FETCH_REPORT_FILE).write_text(json.dumps(fetch_report), encoding="utf-8")
        return seed, cache


if __name__ == "__main__":
    unittest.main()
