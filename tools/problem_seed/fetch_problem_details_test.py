import json
import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools.problem_seed import fetch_problem_details


def graphql_response(slug: str, content: str | None = "<p>Body</p>") -> dict:
    return {
        "data": {
            "question": {
                "questionFrontendId": "1",
                "titleSlug": slug,
                "content": content,
                "translatedContent": content,
            }
        }
    }


class FetchProblemDetailsTest(unittest.TestCase):

    def test_select_rows_filters_slug_before_limit(self) -> None:
        rows = [{"slug": "a"}, {"slug": "b"}, {"slug": "c"}]

        selected = fetch_problem_details.select_rows(rows, ["b", "c"], 1)

        self.assertEqual([{"slug": "b"}], selected)

    @mock.patch("tools.problem_seed.fetch_problem_details.request_json")
    def test_fetch_detail_writes_raw_graphql_response(self, request_json: mock.Mock) -> None:
        request_json.return_value = mock.Mock(status=200, body=graphql_response("two-sum"), error=None)
        with tempfile.TemporaryDirectory() as temp_dir:
            cache_dir = Path(temp_dir)

            result = fetch_problem_details.fetch_detail(
                "two-sum",
                "com",
                cache_dir,
                sleeper=lambda _: None,
            )

            self.assertEqual("fetched", result)
            raw = json.loads((cache_dir / "two-sum.com.json").read_text(encoding="utf-8"))
            self.assertEqual("two-sum", raw["data"]["question"]["titleSlug"])

    @mock.patch("tools.problem_seed.fetch_problem_details.request_json")
    def test_fetch_detail_skips_existing_cache(self, request_json: mock.Mock) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            cache_dir = Path(temp_dir)
            (cache_dir / "two-sum.cn.json").write_text("{}", encoding="utf-8")

            result = fetch_problem_details.fetch_detail("two-sum", "cn", cache_dir)

            self.assertEqual("skipped", result)
            request_json.assert_not_called()

    @mock.patch("tools.problem_seed.fetch_problem_details.request_json")
    def test_fetch_details_sleeps_after_real_fetch_only(self, request_json: mock.Mock) -> None:
        request_json.return_value = mock.Mock(status=200, body=graphql_response("two-sum"), error=None)
        sleeps: list[float] = []
        rows = [{"slug": "two-sum", "onCom": True, "onCn": True}]
        with tempfile.TemporaryDirectory() as temp_dir:
            stats = fetch_problem_details.fetch_details(
                rows,
                Path(temp_dir),
                interval_seconds=0.5,
                sleeper=sleeps.append,
            )

            self.assertEqual(2, stats["fetched"])
            self.assertEqual([0.5, 0.5], sleeps)

    @mock.patch("tools.problem_seed.fetch_problem_details.request_json")
    def test_fetch_detail_does_not_cache_failed_response(self, request_json: mock.Mock) -> None:
        request_json.return_value = mock.Mock(status=0, body=None, error="timeout")
        with tempfile.TemporaryDirectory() as temp_dir:
            cache_dir = Path(temp_dir)

            with contextlib.redirect_stdout(io.StringIO()):
                result = fetch_problem_details.fetch_detail("two-sum", "com", cache_dir)

            self.assertEqual("failed", result)
            self.assertFalse((cache_dir / "two-sum.com.json").exists())


if __name__ == "__main__":
    unittest.main()
