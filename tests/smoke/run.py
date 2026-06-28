from __future__ import annotations

import sys
from pathlib import Path

from lib.context import SmokeContext
from lib.hurl import HurlEnvironmentError, discover_cases, run_hurl_cases
from lib.reports import prepare_report_dir


def main() -> int:
  repo_root = Path(__file__).resolve().parents[2]
  context = SmokeContext.from_env(repo_root)
  suites_root = repo_root / "tests" / "smoke" / "suites"

  try:
    cases = discover_cases(suites_root, context.suite, context.case, context.tags)
    report_dir = prepare_report_dir(context.report_dir, context.run_id)
  except (FileNotFoundError, HurlEnvironmentError) as exception:
    print(f"Smoke test setup failed: {exception}", file=sys.stderr)
    return 2

  print("Smoke test run", flush=True)
  print(f"  base_url: {context.base_url}", flush=True)
  print(f"  run_id: {context.run_id}", flush=True)
  print(f"  suite: {context.suite}", flush=True)
  if context.case:
    print(f"  case: {context.case}", flush=True)
  if context.tags:
    print(f"  tags: {', '.join(context.tags)}", flush=True)
  print(f"  report_dir: {report_dir}", flush=True)
  print("  hurl_files:", flush=True)
  for case in cases:
    print(f"    - {case.path.relative_to(repo_root)}", flush=True)

  return run_hurl_cases(context, cases, report_dir)


if __name__ == "__main__":
  raise SystemExit(main())
