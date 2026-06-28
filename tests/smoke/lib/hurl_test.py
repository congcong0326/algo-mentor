from __future__ import annotations

import tempfile
import unittest
import sys
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from lib.context import SmokeContext
from lib.hurl import HurlCase, run_hurl_cases


class RunHurlCasesTest(unittest.TestCase):

  def test_runs_each_case_with_isolated_cookie_and_report_paths(self) -> None:
    repo_root = Path("/repo")
    cases = [
        HurlCase(
            path=repo_root / "tests" / "smoke" / "suites" / "core" / "auth_and_learning_plan.hurl",
            suite="core",
            name="auth_and_learning_plan",
            tags=frozenset({"critical"}),
        ),
        HurlCase(
            path=repo_root / "tests" / "smoke" / "suites" / "security" / "anonymous_access.hurl",
            suite="security",
            name="anonymous_access",
            tags=frozenset({"critical"}),
        ),
    ]
    context = SmokeContext(
        repo_root=repo_root,
        base_url="http://localhost:8080",
        report_dir=Path("unused"),
        run_id="unit-run",
        suite="all",
        case=None,
        tags=(),
        keep_data=False,
        email="smoke+unit@example.test",
        password="secret",
        display_name="Smoke Unit",
        goal="Smoke Unit Goal",
    )

    with tempfile.TemporaryDirectory() as directory:
      report_dir = Path(directory)
      completed = Mock()
      completed.returncode = 0
      with patch("lib.hurl.ensure_hurl_available"), patch("lib.hurl.subprocess.run", return_value=completed) as run:
        result = run_hurl_cases(context, cases, report_dir)

    self.assertEqual(0, result)
    self.assertEqual(2, run.call_count)

    first_command = run.call_args_list[0].args[0]
    second_command = run.call_args_list[1].args[0]
    self.assertIn(str(report_dir / "core-auth_and_learning_plan" / "cookies.txt"), first_command)
    self.assertIn(str(report_dir / "security-anonymous_access" / "cookies.txt"), second_command)
    self.assertIn(str(report_dir / "core-auth_and_learning_plan" / "html"), first_command)
    self.assertIn(str(report_dir / "security-anonymous_access" / "html"), second_command)
    self.assertEqual(str(cases[0].path), first_command[-1])
    self.assertEqual(str(cases[1].path), second_command[-1])

  def test_continues_after_failed_case_and_returns_failure_code(self) -> None:
    repo_root = Path("/repo")
    cases = [
        HurlCase(path=repo_root / "first.hurl", suite="core", name="first", tags=frozenset()),
        HurlCase(path=repo_root / "second.hurl", suite="security", name="second", tags=frozenset()),
    ]
    context = SmokeContext(
        repo_root=repo_root,
        base_url="http://localhost:8080",
        report_dir=Path("unused"),
        run_id="unit-run",
        suite="all",
        case=None,
        tags=(),
        keep_data=False,
        email="smoke+unit@example.test",
        password="secret",
        display_name="Smoke Unit",
        goal="Smoke Unit Goal",
    )
    failed = Mock()
    failed.returncode = 4
    passed = Mock()
    passed.returncode = 0

    with tempfile.TemporaryDirectory() as directory:
      with patch("lib.hurl.ensure_hurl_available"), patch("lib.hurl.subprocess.run", side_effect=[failed, passed]) as run:
        result = run_hurl_cases(context, cases, Path(directory))

    self.assertEqual(4, result)
    self.assertEqual(2, run.call_count)


if __name__ == "__main__":
  unittest.main()
