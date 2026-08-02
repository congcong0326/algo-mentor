import hashlib
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from tools.learning_plan_template_seed import translate_template_sources as translation


class FakeEngine:
    def translate(self, text: str) -> str:
        return f"English: {text}"


class TranslateTemplateSourcesTest(unittest.TestCase):

    def test_build_translation_keeps_only_translatable_fields_and_phase_indexes(self) -> None:
        source = template_source()
        glossary_config = glossary(source_terms={"中文": "Chinese"})

        result = translation.build_translation(
            "template-one", source, FakeEngine(), glossary_config, overrides()
        )

        self.assertEqual(
            {"templateId", "phases", *translation.TEMPLATE_FIELDS},
            set(result),
        )
        self.assertEqual(1, result["phases"][0]["phaseIndex"])
        self.assertEqual(
            {"phaseIndex", *translation.PHASE_FIELDS},
            set(result["phases"][0]),
        )
        self.assertNotIn("intent", result)
        self.assertNotIn("durationWeeks", result["phases"][0])

    def test_exact_override_and_glossary_are_the_only_correction_inputs(self) -> None:
        source = template_source()
        config = glossary(
            source_terms={"动态规划": "dynamic programming"},
            output_replacements={"English:": "Translated:"},
        )
        exact = overrides({"template-one.title": "Exact English title"})

        result = translation.build_translation("template-one", source, FakeEngine(), config, exact)

        self.assertEqual("Exact English title", result["title"])
        self.assertEqual("Translated: dynamic programming中文", result["phases"][0]["focus"])

    def test_validate_translation_rejects_chinese_and_phase_mismatch(self) -> None:
        source = template_source()
        english = translated_template()
        english["summary"] = "残留中文"

        with self.assertRaisesRegex(translation.TranslationError, "Chinese text remains"):
            translation.validate_translation("template-one", source, english, glossary())

        english["summary"] = "English summary"
        english["phases"][0]["phaseIndex"] = 2
        with self.assertRaisesRegex(translation.TranslationError, "phaseIndex mismatch"):
            translation.validate_translation("template-one", source, english, glossary())

    def test_check_requires_canonical_json_format(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_dir:
            root = Path(temporary_dir)
            template_dir = root / "template-one"
            translation_path = template_dir / translation.TRANSLATION_RELATIVE_PATH
            translation_path.parent.mkdir(parents=True)
            (template_dir / "template.json").write_text(
                json.dumps(template_source(), ensure_ascii=False), encoding="utf-8"
            )
            translation_path.write_text(
                json.dumps(translated_template(), ensure_ascii=False), encoding="utf-8"
            )

            with self.assertRaisesRegex(translation.TranslationError, "canonically formatted"):
                translation.check_translations(root, ["template-one"], glossary())

            translation_path.write_bytes(translation.canonical_json_bytes(translated_template()))
            translation.check_translations(root, ["template-one"], glossary())

    def test_render_translations_finishes_before_any_output_is_written(self) -> None:
        class FailingEngine(FakeEngine):
            def translate(self, text: str) -> str:
                if text == "第二个模板":
                    return ""
                return super().translate(text).replace("中文", "Chinese").replace("动态规划", "dynamic programming")

        with tempfile.TemporaryDirectory() as temporary_dir:
            root = Path(temporary_dir)
            write_source(root, "template-one", template_source())
            second = template_source("template-two")
            second["title"] = "第二个模板"
            write_source(root, "template-two", second)

            with self.assertRaises(translation.TranslationError):
                translation.render_translations(
                    root, ["template-one", "template-two"], FailingEngine(), glossary(), overrides()
                )

            self.assertFalse((root / "template-one" / translation.TRANSLATION_RELATIVE_PATH).exists())
            self.assertFalse((root / "template-two" / translation.TRANSLATION_RELATIVE_PATH).exists())

    def test_verify_model_archive_checks_hash_size_and_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_dir:
            model_path = Path(temporary_dir) / "model.argosmodel"
            with zipfile.ZipFile(model_path, "w") as archive:
                archive.writestr(
                    "translate-zh_en/metadata.json",
                    json.dumps({
                        "from_code": "zh",
                        "to_code": "en",
                        "package_version": "1.9",
                        "argos_version": "1.9.0",
                    }),
                )
            payload = model_path.read_bytes()
            model_lock = {
                "fromCode": "zh",
                "toCode": "en",
                "packageVersion": "1.9",
                "argosVersion": "1.9.0",
                "sizeBytes": len(payload),
                "sha256": hashlib.sha256(payload).hexdigest(),
            }

            translation.verify_model_archive(model_path, model_lock)
            model_lock["sha256"] = "0" * 64
            with self.assertRaisesRegex(translation.TranslationError, "SHA256 mismatch"):
                translation.verify_model_archive(model_path, model_lock)

    def test_download_locked_model_sends_user_agent_and_verifies_archive(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_dir:
            root = Path(temporary_dir)
            source_path = root / "source.argosmodel"
            with zipfile.ZipFile(source_path, "w") as archive:
                archive.writestr(
                    "translate-zh_en/metadata.json",
                    json.dumps({
                        "from_code": "zh",
                        "to_code": "en",
                        "package_version": "1.9",
                        "argos_version": "1.9.0",
                    }),
                )
            payload = source_path.read_bytes()
            lock = {
                "model": {
                    "source": "https://example.test/model.argosmodel",
                    "fromCode": "zh",
                    "toCode": "en",
                    "packageVersion": "1.9",
                    "argosVersion": "1.9.0",
                    "sizeBytes": len(payload),
                    "sha256": hashlib.sha256(payload).hexdigest(),
                },
            }
            target = root / "cache" / "model.argosmodel"

            with patch.object(translation.urllib.request, "urlopen", return_value=io.BytesIO(payload)) as urlopen:
                translation.download_locked_model(lock, target)

            request = urlopen.call_args.args[0]
            self.assertEqual(translation.MODEL_DOWNLOAD_USER_AGENT, request.get_header("User-agent"))
            self.assertEqual(translation.MODEL_DOWNLOAD_TIMEOUT_SECONDS, urlopen.call_args.kwargs["timeout"])
            self.assertEqual(payload, target.read_bytes())

    def test_current_source_structure_matches_locked_baseline(self) -> None:
        lock = translation.read_json(translation.DEFAULT_LOCK_PATH)

        actual = translation.source_structure_sha256(
            translation.DEFAULT_TEMPLATE_ROOT,
            translation.DEFAULT_TEMPLATE_ORDER,
        )

        self.assertEqual(lock["sourceStructureSha256"], actual)


def template_source(template_id: str = "template-one") -> dict:
    return {
        "templateId": template_id,
        "title": "中文标题",
        "summary": "中文摘要",
        "goal": "中文目标",
        "targetAudience": "中文用户",
        "prerequisites": ["中文基础"],
        "recommendedFor": ["中文推荐"],
        "notRecommendedFor": ["中文不推荐"],
        "expectedOutcome": "中文结果",
        "intent": "SYSTEMATIC_LEARNING",
        "phases": [{
            "phaseIndex": 1,
            "durationWeeks": 1,
            "title": "中文阶段",
            "focus": "动态规划中文",
            "objectives": ["中文目标一"],
            "acceptanceCriteria": ["中文验收"],
            "reviewAdvice": "中文复盘",
        }],
    }


def translated_template() -> dict:
    return {
        "templateId": "template-one",
        "title": "English title",
        "summary": "English summary",
        "goal": "English goal",
        "targetAudience": "English audience",
        "prerequisites": ["English prerequisite"],
        "recommendedFor": ["English recommendation"],
        "notRecommendedFor": ["English exclusion"],
        "expectedOutcome": "English outcome",
        "phases": [{
            "phaseIndex": 1,
            "title": "English phase",
            "focus": "English focus",
            "objectives": ["English objective"],
            "acceptanceCriteria": ["English acceptance"],
            "reviewAdvice": "English review advice",
        }],
    }


def glossary(source_terms=None, output_replacements=None) -> dict:
    return {
        "schemaVersion": 1,
        "sourceTerms": source_terms or {},
        "outputReplacements": output_replacements or {},
        "allowedChineseTerms": [],
    }


def overrides(values=None) -> dict:
    return {"schemaVersion": 1, "translations": values or {}}


def write_source(root: Path, template_id: str, source: dict) -> None:
    template_dir = root / template_id
    template_dir.mkdir(parents=True)
    (template_dir / "template.json").write_text(
        json.dumps(source, ensure_ascii=False), encoding="utf-8"
    )


if __name__ == "__main__":
    unittest.main()
