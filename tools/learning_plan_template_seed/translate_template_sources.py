#!/usr/bin/env python3
"""使用固定 Argos 模型生成并校验学习计划模板英文内容。"""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path
from typing import Any, Protocol


DEFAULT_SOURCE_ROOT = Path("data/learning-plan-template-sources")
DEFAULT_TEMPLATE_ROOT = DEFAULT_SOURCE_ROOT / "templates"
DEFAULT_TEMPLATE_ORDER = DEFAULT_SOURCE_ROOT / "template_order.json"
DEFAULT_LOCK_PATH = Path("tools/learning_plan_template_seed/template_translation_lock.json")
DEFAULT_GLOSSARY_PATH = Path("tools/learning_plan_template_seed/template_translation_glossary.json")
DEFAULT_OVERRIDES_PATH = Path("tools/learning_plan_template_seed/template_translation_overrides.json")
DEFAULT_MODEL_PATH = Path(".cache/learning-plan-template-translation/translate-zh_en-1_9.argosmodel")
MODEL_DOWNLOAD_USER_AGENT = "algo-mentor-template-translator/1.0"
MODEL_DOWNLOAD_TIMEOUT_SECONDS = 60
TRANSLATION_RELATIVE_PATH = Path("translations/en-US.json")

TEMPLATE_FIELDS = (
    "title",
    "summary",
    "goal",
    "targetAudience",
    "prerequisites",
    "recommendedFor",
    "notRecommendedFor",
    "expectedOutcome",
)
PHASE_FIELDS = (
    "title",
    "focus",
    "objectives",
    "acceptanceCriteria",
    "reviewAdvice",
)
HAN_RE = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")


class TranslationEngine(Protocol):
    def translate(self, text: str) -> str:
        """Translate one non-empty zh-CN string to en-US."""


class TranslationError(RuntimeError):
    """Raised when deterministic translation cannot complete safely."""


def read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exception:
        raise TranslationError(f"Required file is unavailable: {path}") from exception
    except json.JSONDecodeError as exception:
        raise TranslationError(f"Invalid JSON in {path}: {exception}") from exception
    if not isinstance(value, dict):
        raise TranslationError(f"Expected a JSON object in {path}")
    return value


def load_template_ids(order_path: Path, selected: list[str] | None = None) -> list[str]:
    order = read_json(order_path).get("templateIds")
    if not isinstance(order, list) or not order or not all(isinstance(item, str) and item for item in order):
        raise TranslationError(f"templateIds must be a non-empty string list in {order_path}")
    if len(order) != len(set(order)):
        raise TranslationError(f"Duplicate templateId in {order_path}")
    if not selected:
        return order
    selected_set = set(selected)
    unknown = sorted(selected_set.difference(order))
    if unknown:
        raise TranslationError(f"Unknown templateId(s): {', '.join(unknown)}")
    return [template_id for template_id in order if template_id in selected_set]


def canonical_json_bytes(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def load_translation_config(
    lock_path: Path,
    glossary_path: Path,
    overrides_path: Path,
) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    lock = read_json(lock_path)
    glossary = read_json(glossary_path)
    overrides = read_json(overrides_path)
    for path, value in ((lock_path, lock), (glossary_path, glossary), (overrides_path, overrides)):
        if value.get("schemaVersion") != 1:
            raise TranslationError(f"Unsupported schemaVersion in {path}")
    if lock.get("engine", {}).get("name") != "Argos Translate":
        raise TranslationError("Only the locked Argos Translate engine is supported")
    if not isinstance(glossary.get("sourceTerms"), dict):
        raise TranslationError("sourceTerms must be an object")
    if not isinstance(glossary.get("outputReplacements"), dict):
        raise TranslationError("outputReplacements must be an object")
    if not isinstance(glossary.get("allowedChineseTerms"), list):
        raise TranslationError("allowedChineseTerms must be a list")
    if not isinstance(overrides.get("translations"), dict):
        raise TranslationError("translations overrides must be an object")
    return lock, glossary, overrides


def source_structure_sha256(template_root: Path, order_path: Path) -> str:
    items: list[dict[str, Any]] = []
    for template_id in load_template_ids(order_path):
        template_dir = template_root / template_id
        source = read_json(template_dir / "template.json")
        clean_template = {key: value for key, value in source.items() if key not in TEMPLATE_FIELDS}
        phases = source.get("phases")
        if not isinstance(phases, list):
            raise TranslationError(f"phases must be a list for {template_id}")
        clean_template["phases"] = [
            {key: value for key, value in phase.items() if key not in PHASE_FIELDS}
            for phase in phases
        ]
        refs_path = template_dir / "problem_refs.jsonl"
        try:
            refs = [json.loads(line) for line in refs_path.read_text(encoding="utf-8").splitlines() if line.strip()]
        except (FileNotFoundError, json.JSONDecodeError) as exception:
            raise TranslationError(f"Invalid problem refs for {template_id}: {exception}") from exception
        items.append({"template": clean_template, "problemRefs": refs})
    payload = json.dumps(items, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def validate_source_structure(lock: dict[str, Any], template_root: Path, order_path: Path) -> None:
    expected = lock.get("sourceStructureSha256")
    actual = source_structure_sha256(template_root, order_path)
    if not isinstance(expected, str) or actual != expected:
        raise TranslationError(
            "Template source structure changed outside translatable fields: "
            f"expected {expected}, got {actual}"
        )


def translation_key(template_id: str, field: str, phase_index: int | None = None) -> str:
    if phase_index is None:
        return f"{template_id}.{field}"
    return f"{template_id}.phases[{phase_index}].{field}"


def translate_value(
    value: Any,
    key: str,
    engine: TranslationEngine,
    glossary: dict[str, Any],
    overrides: dict[str, Any],
) -> Any:
    if isinstance(value, list):
        if not value:
            raise TranslationError(f"Translatable list cannot be empty: {key}")
        return [translate_value(item, f"{key}[{index}]", engine, glossary, overrides) for index, item in enumerate(value)]
    if not isinstance(value, str) or not value.strip():
        raise TranslationError(f"Translatable value must be a non-empty string or string list: {key}")

    exact = overrides["translations"].get(key)
    if exact is not None:
        if not isinstance(exact, str) or not exact.strip():
            raise TranslationError(f"Exact translation override must be a non-empty string: {key}")
        return exact.strip()
    translated = engine.translate(value)
    if not isinstance(translated, str) or not translated.strip():
        raise TranslationError(f"Translation engine returned an empty value for {key}")

    for source_term, target_term in sorted(
        glossary["sourceTerms"].items(), key=lambda item: (-len(item[0]), item[0])
    ):
        if not isinstance(source_term, str) or not isinstance(target_term, str):
            raise TranslationError("Glossary sourceTerms must map strings to strings")
        translated = translated.replace(source_term, target_term)
    for source_term, target_term in sorted(glossary["outputReplacements"].items()):
        if not isinstance(source_term, str) or not isinstance(target_term, str):
            raise TranslationError("Glossary outputReplacements must map strings to strings")
        translated = translated.replace(source_term, target_term)
    return translated.strip()


def build_translation(
    template_id: str,
    source: dict[str, Any],
    engine: TranslationEngine,
    glossary: dict[str, Any],
    overrides: dict[str, Any],
) -> dict[str, Any]:
    if source.get("templateId") != template_id:
        raise TranslationError(f"templateId mismatch in source for {template_id}")
    result: dict[str, Any] = {"templateId": template_id}
    for field in TEMPLATE_FIELDS:
        if field not in source:
            raise TranslationError(f"Missing source field {template_id}.{field}")
        result[field] = translate_value(
            source[field], translation_key(template_id, field), engine, glossary, overrides
        )

    phases = source.get("phases")
    if not isinstance(phases, list) or not phases:
        raise TranslationError(f"Template {template_id} must contain phases")
    result_phases: list[dict[str, Any]] = []
    for expected_index, phase in enumerate(phases, start=1):
        if not isinstance(phase, dict) or phase.get("phaseIndex") != expected_index:
            raise TranslationError(f"Template {template_id} phaseIndex must be continuous from 1")
        translated_phase: dict[str, Any] = {"phaseIndex": expected_index}
        for field in PHASE_FIELDS:
            if field not in phase:
                raise TranslationError(f"Missing source field {template_id}.phases[{expected_index}].{field}")
            translated_phase[field] = translate_value(
                phase[field],
                translation_key(template_id, field, expected_index),
                engine,
                glossary,
                overrides,
            )
        result_phases.append(translated_phase)
    result["phases"] = result_phases
    return result


def iter_strings(value: Any, path: str = "$"):
    if isinstance(value, str):
        yield path, value
    elif isinstance(value, list):
        for index, item in enumerate(value):
            yield from iter_strings(item, f"{path}[{index}]")
    elif isinstance(value, dict):
        for key, item in value.items():
            yield from iter_strings(item, f"{path}.{key}")


def validate_translation(
    template_id: str,
    source: dict[str, Any],
    translation: dict[str, Any],
    glossary: dict[str, Any],
) -> None:
    expected_template_keys = {"templateId", "phases", *TEMPLATE_FIELDS}
    if set(translation) != expected_template_keys:
        raise TranslationError(f"Unexpected translation fields for {template_id}: {sorted(translation)}")
    if translation.get("templateId") != template_id:
        raise TranslationError(f"Translation templateId mismatch for {template_id}")
    phases = translation.get("phases")
    source_phases = source.get("phases")
    if not isinstance(phases, list) or not isinstance(source_phases, list) or len(phases) != len(source_phases):
        raise TranslationError(f"Translation phase count mismatch for {template_id}")

    for field in TEMPLATE_FIELDS:
        validate_translated_shape(source[field], translation[field], f"{template_id}.{field}")
    expected_phase_keys = {"phaseIndex", *PHASE_FIELDS}
    for source_phase, translated_phase in zip(source_phases, phases, strict=True):
        if not isinstance(translated_phase, dict) or set(translated_phase) != expected_phase_keys:
            raise TranslationError(f"Unexpected translated phase fields for {template_id}")
        if translated_phase.get("phaseIndex") != source_phase.get("phaseIndex"):
            raise TranslationError(f"Translation phaseIndex mismatch for {template_id}")
        for field in PHASE_FIELDS:
            validate_translated_shape(
                source_phase[field],
                translated_phase[field],
                f"{template_id}.phases[{source_phase['phaseIndex']}].{field}",
            )

    allowed_terms = glossary["allowedChineseTerms"]
    for path, value in iter_strings(translation):
        cleaned = value
        for allowed in allowed_terms:
            if not isinstance(allowed, str):
                raise TranslationError("allowedChineseTerms must contain strings")
            cleaned = cleaned.replace(allowed, "")
        if HAN_RE.search(cleaned):
            raise TranslationError(f"Unapproved Chinese text remains at {template_id}{path[1:]}: {value}")


def validate_translated_shape(source: Any, translated: Any, key: str) -> None:
    if isinstance(source, list):
        if not isinstance(translated, list) or len(source) != len(translated):
            raise TranslationError(f"Translated list shape mismatch: {key}")
        for index, item in enumerate(translated):
            if not isinstance(item, str) or not item.strip():
                raise TranslationError(f"Empty translated list item: {key}[{index}]")
        return
    if not isinstance(translated, str) or not translated.strip():
        raise TranslationError(f"Empty translated value: {key}")


def render_translations(
    template_root: Path,
    template_ids: list[str],
    engine: TranslationEngine,
    glossary: dict[str, Any],
    overrides: dict[str, Any],
) -> dict[str, bytes]:
    rendered: dict[str, bytes] = {}
    for index, template_id in enumerate(template_ids, start=1):
        print(f"Translating {index}/{len(template_ids)}: {template_id}", file=sys.stderr, flush=True)
        source = read_json(template_root / template_id / "template.json")
        translation = build_translation(template_id, source, engine, glossary, overrides)
        validate_translation(template_id, source, translation, glossary)
        rendered[template_id] = canonical_json_bytes(translation)
    return rendered


def write_translations(template_root: Path, rendered: dict[str, bytes]) -> None:
    staged: list[tuple[Path, Path]] = []
    backups: dict[Path, bytes | None] = {}
    replaced: list[Path] = []
    with tempfile.TemporaryDirectory(
        prefix=".learning-plan-translations-",
        dir=template_root,
    ) as temporary_dir:
        staging_root = Path(temporary_dir)
        for template_id, content in rendered.items():
            staged_path = staging_root / template_id / TRANSLATION_RELATIVE_PATH
            staged_path.parent.mkdir(parents=True, exist_ok=True)
            staged_path.write_bytes(content)
            target = template_root / template_id / TRANSLATION_RELATIVE_PATH
            backups[target] = target.read_bytes() if target.is_file() else None
            staged.append((staged_path, target))
        try:
            for staged_path, target in staged:
                target.parent.mkdir(parents=True, exist_ok=True)
                os.replace(staged_path, target)
                replaced.append(target)
        except OSError:
            for target in reversed(replaced):
                previous = backups[target]
                if previous is None:
                    target.unlink(missing_ok=True)
                else:
                    target.write_bytes(previous)
            raise


def check_translations(
    template_root: Path,
    template_ids: list[str],
    glossary: dict[str, Any],
) -> None:
    for template_id in template_ids:
        source = read_json(template_root / template_id / "template.json")
        path = template_root / template_id / TRANSLATION_RELATIVE_PATH
        translation = read_json(path)
        validate_translation(template_id, source, translation, glossary)
        if path.read_bytes() != canonical_json_bytes(translation):
            raise TranslationError(f"Translation JSON is not canonically formatted: {path}")


def verify_file(path: Path, expected_size: int, expected_sha256: str) -> None:
    if not path.is_file():
        raise TranslationError(f"Locked Argos model is unavailable: {path}")
    actual_size = path.stat().st_size
    if actual_size != expected_size:
        raise TranslationError(f"Argos model size mismatch: expected {expected_size}, got {actual_size}")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != expected_sha256:
        raise TranslationError(f"Argos model SHA256 mismatch: expected {expected_sha256}, got {digest}")


def verify_model_archive(path: Path, model_lock: dict[str, Any]) -> None:
    verify_file(path, model_lock["sizeBytes"], model_lock["sha256"])
    try:
        with zipfile.ZipFile(path) as archive:
            metadata_names = [name for name in archive.namelist() if name.endswith("/metadata.json")]
            if len(metadata_names) != 1:
                raise TranslationError("Argos model must contain exactly one package metadata.json")
            metadata = json.loads(archive.read(metadata_names[0]).decode("utf-8"))
    except (zipfile.BadZipFile, json.JSONDecodeError, UnicodeDecodeError) as exception:
        raise TranslationError(f"Invalid Argos model archive: {path}") from exception
    expected = {
        "from_code": model_lock["fromCode"],
        "to_code": model_lock["toCode"],
        "package_version": model_lock["packageVersion"],
        "argos_version": model_lock["argosVersion"],
    }
    actual = {key: metadata.get(key) for key in expected}
    if actual != expected:
        raise TranslationError(f"Argos model metadata mismatch: expected {expected}, got {actual}")


def configure_argos_environment(decoding: dict[str, Any], isolated_root: Path) -> None:
    values = {
        "ARGOS_DEVICE_TYPE": decoding["deviceType"],
        "ARGOS_INTER_THREADS": str(decoding["interThreads"]),
        "ARGOS_INTRA_THREADS": str(decoding["intraThreads"]),
        "ARGOS_BATCH_SIZE": str(decoding["batchSize"]),
        "ARGOS_BEAM_SIZE": str(decoding["beamSize"]),
        "ARGOS_COMPUTE_TYPE": decoding["computeType"],
        "ARGOS_MODEL_PROVIDER": "OPENNMT",
        "OMP_NUM_THREADS": "1",
        "OPENBLAS_NUM_THREADS": "1",
        "MKL_NUM_THREADS": "1",
        "XDG_DATA_HOME": str(isolated_root / "data"),
        "XDG_CACHE_HOME": str(isolated_root / "cache"),
        "XDG_CONFIG_HOME": str(isolated_root / "config"),
        "ARGOS_PACKAGES_DIR": str(isolated_root / "packages"),
    }
    for key, value in values.items():
        os.environ[key] = value


class ArgosTranslationEngine:
    def __init__(self, lock: dict[str, Any], model_path: Path):
        model_lock = lock["model"]
        verify_model_archive(model_path, model_lock)
        self._temporary_dir = tempfile.TemporaryDirectory(prefix="algo-mentor-argos-")
        configure_argos_environment(lock["decoding"], Path(self._temporary_dir.name))
        try:
            installed_version = importlib.metadata.version(lock["engine"]["pythonPackage"])
        except importlib.metadata.PackageNotFoundError as exception:
            raise TranslationError(
                f"{lock['engine']['pythonPackage']}=={lock['engine']['version']} is required for --translate"
            ) from exception
        if installed_version != lock["engine"]["version"]:
            raise TranslationError(
                f"Argos Translate version mismatch: expected {lock['engine']['version']}, got {installed_version}"
            )
        try:
            import argostranslate.package
            import argostranslate.translate

            argostranslate.package.install_from_path(model_path)
            translation = argostranslate.translate.get_translation_from_codes(
                model_lock["fromCode"], model_lock["toCode"]
            )
        except Exception as exception:
            raise TranslationError(f"Unable to load the locked Argos model: {exception}") from exception
        if translation is None:
            raise TranslationError("Locked Argos zh -> en translation was not installed")
        self._translation = translation
        self._cache: dict[str, str] = {}

    def translate(self, text: str) -> str:
        cached = self._cache.get(text)
        if cached is not None:
            return cached
        try:
            translated = self._translation.translate(text)
        except Exception as exception:
            raise TranslationError(f"Argos failed to translate input: {exception}") from exception
        self._cache[text] = translated
        return translated


def download_locked_model(lock: dict[str, Any], target: Path) -> None:
    model_lock = lock["model"]
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as temporary_file:
        temporary_path = Path(temporary_file.name)
    try:
        try:
            request = urllib.request.Request(
                model_lock["source"],
                headers={"User-Agent": MODEL_DOWNLOAD_USER_AGENT},
            )
            with urllib.request.urlopen(request, timeout=MODEL_DOWNLOAD_TIMEOUT_SECONDS) as response:
                with temporary_path.open("wb") as output:
                    shutil.copyfileobj(response, output)
        except OSError as exception:
            raise TranslationError(f"Unable to download locked Argos model: {exception}") from exception
        verify_model_archive(temporary_path, model_lock)
        os.replace(temporary_path, target)
    finally:
        temporary_path.unlink(missing_ok=True)


def compare_rendered(expected: dict[str, bytes], actual: dict[str, bytes], label: str) -> None:
    if expected.keys() != actual.keys():
        raise TranslationError(f"Determinism key mismatch for {label}")
    changed = [template_id for template_id in expected if expected[template_id] != actual[template_id]]
    if changed:
        raise TranslationError(f"Byte-level determinism failed for {label}: {', '.join(changed)}")


def committed_rendered(template_root: Path, template_ids: list[str]) -> dict[str, bytes]:
    result: dict[str, bytes] = {}
    for template_id in template_ids:
        path = template_root / template_id / TRANSLATION_RELATIVE_PATH
        if not path.is_file():
            raise TranslationError(f"Committed translation is unavailable: {path}")
        result[template_id] = path.read_bytes()
    return result


def write_rendered_root(output_root: Path, rendered: dict[str, bytes]) -> None:
    for template_id, content in rendered.items():
        path = output_root / template_id / "en-US.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)


def read_rendered_root(output_root: Path, template_ids: list[str]) -> dict[str, bytes]:
    return {
        template_id: (output_root / template_id / "en-US.json").read_bytes()
        for template_id in template_ids
    }


def render_in_fresh_process(args: argparse.Namespace, output_root: Path) -> None:
    command = [
        sys.executable,
        str(Path(__file__).resolve()),
        "--render-to",
        str(output_root),
        "--template-root",
        args.template_root,
        "--template-order",
        args.template_order,
        "--lock",
        args.lock,
        "--glossary",
        args.glossary,
        "--overrides",
        args.overrides,
        "--model-path",
        args.model_path,
    ]
    for template_id in args.template_ids or []:
        command.extend(("--template-id", template_id))
    try:
        subprocess.run(command, check=True)
    except subprocess.CalledProcessError as exception:
        raise TranslationError(f"Fresh deterministic render failed with exit code {exception.returncode}") from exception


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--translate", action="store_true", help="Generate committed en-US translation files")
    mode.add_argument("--check", action="store_true", help="Validate committed files without Argos or network")
    mode.add_argument("--verify-determinism", action="store_true", help="Generate twice and compare byte-for-byte")
    mode.add_argument("--download-model", action="store_true", help="Download only the locked Argos model")
    mode.add_argument("--render-to", help=argparse.SUPPRESS)
    parser.add_argument("--template-root", default=str(DEFAULT_TEMPLATE_ROOT))
    parser.add_argument("--template-order", default=str(DEFAULT_TEMPLATE_ORDER))
    parser.add_argument("--lock", default=str(DEFAULT_LOCK_PATH))
    parser.add_argument("--glossary", default=str(DEFAULT_GLOSSARY_PATH))
    parser.add_argument("--overrides", default=str(DEFAULT_OVERRIDES_PATH))
    parser.add_argument("--model-path", default=str(DEFAULT_MODEL_PATH))
    parser.add_argument("--template-id", action="append", dest="template_ids")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    template_root = Path(args.template_root)
    order_path = Path(args.template_order)
    lock, glossary, overrides = load_translation_config(
        Path(args.lock), Path(args.glossary), Path(args.overrides)
    )
    if args.download_model:
        download_locked_model(lock, Path(args.model_path))
        print(f"Downloaded locked Argos model to {args.model_path}")
        return

    validate_source_structure(lock, template_root, order_path)
    template_ids = load_template_ids(order_path, args.template_ids)
    if args.check:
        check_translations(template_root, template_ids, glossary)
        print(f"Checked {len(template_ids)} template translation(s)")
        return

    if args.render_to:
        engine = ArgosTranslationEngine(lock, Path(args.model_path))
        rendered = render_translations(template_root, template_ids, engine, glossary, overrides)
        write_rendered_root(Path(args.render_to), rendered)
        return

    if args.verify_determinism:
        with tempfile.TemporaryDirectory(prefix="learning-plan-determinism-a-") as first_dir:
            with tempfile.TemporaryDirectory(prefix="learning-plan-determinism-b-") as second_dir:
                render_in_fresh_process(args, Path(first_dir))
                render_in_fresh_process(args, Path(second_dir))
                first = read_rendered_root(Path(first_dir), template_ids)
                second = read_rendered_root(Path(second_dir), template_ids)
        compare_rendered(first, second, "two fresh runs")
        compare_rendered(first, committed_rendered(template_root, template_ids), "committed translations")
        print(f"Verified byte-level determinism for {len(template_ids)} template(s)")
        return

    engine = ArgosTranslationEngine(lock, Path(args.model_path))
    first = render_translations(template_root, template_ids, engine, glossary, overrides)
    if args.translate:
        write_translations(template_root, first)
        print(f"Translated {len(template_ids)} template(s)")
        return



if __name__ == "__main__":
    try:
        main()
    except TranslationError as exception:
        print(f"error: {exception}", file=sys.stderr)
        raise SystemExit(1) from exception
