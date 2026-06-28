from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path

from .context import SmokeContext


@dataclass(frozen=True)
class HurlCase:
  path: Path
  suite: str
  name: str
  tags: frozenset[str]


class HurlEnvironmentError(RuntimeError):
  pass


def ensure_hurl_available() -> None:
  if shutil.which("hurl") is None:
    raise HurlEnvironmentError("hurl is not installed or not available on PATH.")


def discover_cases(suites_root: Path, suite_selector: str, case_name: str | None, tags: tuple[str, ...]) -> list[HurlCase]:
  selected_suites = parse_suite_selector(suite_selector, suites_root)
  cases: list[HurlCase] = []
  for suite in selected_suites:
    suite_dir = suites_root / suite
    for path in sorted(suite_dir.rglob("*.hurl")):
      hurl_case = HurlCase(path=path, suite=suite, name=path.stem, tags=parse_tags(path))
      if case_name is not None and hurl_case.name != case_name:
        continue
      if tags and not set(tags).issubset(hurl_case.tags):
        continue
      cases.append(hurl_case)
  if not cases:
    available = available_cases(suites_root)
    filters = f"SMOKE_SUITE={suite_selector}"
    if case_name:
      filters += f" SMOKE_CASE={case_name}"
    if tags:
      filters += f" SMOKE_TAGS={','.join(tags)}"
    raise FileNotFoundError(f"No smoke cases matched {filters}. Available cases: {available}")
  return cases


def run_hurl_cases(context: SmokeContext, cases: list[HurlCase], report_dir: Path) -> int:
  ensure_hurl_available()
  cookie_file = report_dir / "cookies.txt"
  command = [
      "hurl",
      "--test",
      "--jobs",
      "1",
      "--error-format",
      "short",
      "--report-html",
      str(report_dir / "html"),
      "--report-json",
      str(report_dir / "json"),
      "--report-junit",
      str(report_dir / "junit.xml"),
      "--cookie",
      str(cookie_file),
      "--cookie-jar",
      str(cookie_file),
  ]
  for name, value in context.hurl_variables().items():
    option = "--secret" if name == "smoke_password" else "--variable"
    command.extend([option, f"{name}={value}"])
  command.extend(str(case.path) for case in cases)
  result = subprocess.run(command, cwd=context.repo_root)
  return result.returncode


def parse_suite_selector(selector: str, suites_root: Path) -> list[str]:
  requested = [part.strip() for part in selector.split(",") if part.strip()]
  if not requested:
    requested = ["core"]
  if requested == ["all"]:
    return sorted(path.name for path in suites_root.iterdir() if path.is_dir())
  missing = [suite for suite in requested if not (suites_root / suite).is_dir()]
  if missing:
    available = ", ".join(sorted(path.name for path in suites_root.iterdir() if path.is_dir()))
    raise FileNotFoundError(f"Unknown smoke suite(s): {', '.join(missing)}. Available suites: {available}")
  return requested


def parse_tags(path: Path) -> frozenset[str]:
  tags: set[str] = set()
  with path.open(encoding="utf-8") as handle:
    for line in handle:
      stripped = line.strip()
      if not stripped:
        continue
      if not stripped.startswith("#"):
        break
      if stripped.startswith("# tags:"):
        tags.update(tag.strip() for tag in stripped.removeprefix("# tags:").split(",") if tag.strip())
  return frozenset(tags)


def available_cases(suites_root: Path) -> str:
  cases: list[str] = []
  for suite_dir in sorted(path for path in suites_root.iterdir() if path.is_dir()):
    for path in sorted(suite_dir.rglob("*.hurl")):
      cases.append(f"{suite_dir.name}/{path.stem}")
  return ", ".join(cases) if cases else "(none)"
