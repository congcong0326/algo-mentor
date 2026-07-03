import json
import tempfile
import unittest
from pathlib import Path

from tools.problem_company_seed import prepare_company_seed as seed


class PrepareCompanySeedTest(unittest.TestCase):

    def test_extract_problem_slug_supports_leetcode_domains(self) -> None:
        self.assertEqual("two-sum", seed.extract_problem_slug("https://leetcode.com/problems/two-sum/?envType=company"))
        self.assertEqual("add-strings", seed.extract_problem_slug("https://leetcode-cn.com/problems/add-strings"))
        self.assertEqual("cnHoX6", seed.extract_problem_slug("https://leetcode.cn/problems/cnHoX6/"))
        self.assertIsNone(seed.extract_problem_slug("https://mp.weixin.qq.com/s/example"))

    def test_parse_leetcode_top_markdown_rows(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            source = Path(temp_dir)
            company = source / "tencent"
            company.mkdir()
            (company / "frontend.md").write_text(
                "| 题目 | 出现次数 | 链接 |\n"
                "|---|---:|---|\n"
                "| 415. 字符串相加 | 14 | https://leetcode-cn.com/problems/add-strings |\n"
                "| 非题目 | 1 | https://mp.weixin.qq.com/s/example |\n",
                encoding="utf-8",
            )

            signals, errors = seed.parse_leetcode_top_source(source)

            self.assertEqual(1, len(signals))
            self.assertEqual("tencent", signals[0].company_slug)
            self.assertEqual("腾讯", signals[0].company_name)
            self.assertEqual("CHINA", signals[0].company_market)
            self.assertEqual("FRONTEND", signals[0].role)
            self.assertEqual("add-strings", signals[0].problem_slug)
            self.assertEqual("ALL_TIME", signals[0].recency_bucket)
            self.assertEqual(14, seed.decimal_to_number(signals[0].frequency_score))
            self.assertEqual("non_problem_link", errors[0]["rule"])

    def test_parse_leetcode_top_five_column_markdown_rows(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            source = Path(temp_dir)
            company = source / "baidu"
            company.mkdir()
            (company / "algorithm.md").write_text(
                "| 公司 | 岗位 | 题目 | 链接 | 频度 |\n"
                "|----|----|----|----|----|\n"
                "| 百度 | 算法 | 215. 数组中的第K个最大元素 | https://leetcode-cn.com/problems/kth-largest-element-in-an-array/ | 7 |\n",
                encoding="utf-8",
            )

            signals, errors = seed.parse_leetcode_top_source(source)

            self.assertEqual([], errors)
            self.assertEqual(1, len(signals))
            self.assertEqual("baidu", signals[0].company_slug)
            self.assertEqual("ALGORITHM", signals[0].role)
            self.assertEqual("kth-largest-element-in-an-array", signals[0].problem_slug)
            self.assertEqual(7, seed.decimal_to_number(signals[0].frequency_score))

    def test_parse_leetcode_top_rows_without_link_are_reported(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            source = Path(temp_dir)
            company = source / "alibaba"
            company.mkdir()
            (company / "backend.md").write_text(
                "| 算法题 | 次数 |\n"
                "|---|---|\n"
                "| 1. 两数之和 | 2 |\n",
                encoding="utf-8",
            )

            signals, errors = seed.parse_leetcode_top_source(source)

            self.assertEqual([], signals)
            self.assertEqual("missing_problem_link", errors[0]["rule"])

    def test_unknown_leetcode_top_role_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            source = Path(temp_dir)
            company = source / "tencent"
            company.mkdir()
            (company / "pm.md").write_text("| 题目 | 出现次数 | 链接 |\n| A | 1 | https://leetcode.cn/problems/two-sum |\n", encoding="utf-8")

            signals, errors = seed.parse_leetcode_top_source(source)

            self.assertEqual([], signals)
            self.assertEqual("unknown_role", errors[0]["rule"])

    def test_parse_liquidslr_csv_maps_bucket_and_acceptance_rate(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            source = Path(temp_dir)
            company = source / "Amazon"
            company.mkdir()
            (company / "1. Thirty Days.csv").write_text(
                "Difficulty,Title,Frequency,Acceptance Rate,Link,Topics\n"
                "Easy,Two Sum,100,54.5%,https://leetcode.com/problems/two-sum,array\n",
                encoding="utf-8",
            )

            signals, errors = seed.parse_liquidslr_source(source)

            self.assertEqual([], errors)
            self.assertEqual(1, len(signals))
            self.assertEqual("amazon", signals[0].company_slug)
            self.assertEqual("OTHER", signals[0].company_market)
            self.assertEqual("GENERAL", signals[0].role)
            self.assertEqual("THIRTY_DAYS", signals[0].recency_bucket)
            self.assertEqual(100, seed.decimal_to_number(signals[0].frequency_score))
            self.assertEqual(0.545, seed.decimal_to_number(signals[0].acceptance_rate))

    def test_dedupe_keeps_higher_frequency_and_assigns_rank(self) -> None:
        low = seed.CompanySignal("tencent", "腾讯", "CHINA", "BACKEND", "two-sum", "ALL_TIME", seed.Decimal("1"), None, None, "src", "url", None, "url/1", 1)
        high = seed.CompanySignal("tencent", "腾讯", "CHINA", "BACKEND", "two-sum", "ALL_TIME", seed.Decimal("3"), None, None, "src", "url", None, "url/2", 2)
        other = seed.CompanySignal("tencent", "腾讯", "CHINA", "BACKEND", "add-strings", "ALL_TIME", seed.Decimal("2"), None, None, "src", "url", None, "url/3", 3)

        deduped, duplicate_count = seed.dedupe_signals([low, high, other])
        ranked = seed.assign_ranks(deduped)

        self.assertEqual(1, duplicate_count)
        self.assertEqual("two-sum", ranked[0].problem_slug)
        self.assertEqual(1, ranked[0].rank)
        self.assertEqual("add-strings", ranked[1].problem_slug)
        self.assertEqual(2, ranked[1].rank)

    def test_build_company_seed_validates_against_local_problem_seed(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            liquidslr = root / "liquidslr"
            leetcode_top = root / "top"
            (liquidslr / "Amazon").mkdir(parents=True)
            (leetcode_top / "tencent").mkdir(parents=True)
            (liquidslr / "Amazon" / "5. All.csv").write_text(
                "Difficulty,Title,Frequency,Acceptance Rate,Link,Topics\n"
                "Easy,Two Sum,100,0.5,https://leetcode.com/problems/two-sum,array\n",
                encoding="utf-8",
            )
            (leetcode_top / "tencent" / "backend.md").write_text(
                "| 题目 | 出现次数 | 链接 |\n"
                "|---|---|---|\n"
                "| 415. 字符串相加 | 14 | https://leetcode-cn.com/problems/add-strings |\n",
                encoding="utf-8",
            )
            problem_seed = root / "problems.jsonl"
            problem_seed.write_text(json.dumps({"slug": "two-sum"}) + "\n", encoding="utf-8")

            signals, report = seed.build_company_seed(
                liquidslr_dir=liquidslr,
                leetcode_top_dir=leetcode_top,
                local_problems_path=problem_seed,
            )

            self.assertEqual(1, len(signals))
            self.assertEqual("two-sum", signals[0].problem_slug)
            self.assertEqual(1, report["matchedSignalCount"])
            self.assertEqual(1, report["unmatchedProblemCount"])
            self.assertEqual({"OTHER": 1}, report["companyMarkets"])
            self.assertEqual("add-strings", report["unmatchedProblems"][0]["problemSlug"])


if __name__ == "__main__":
    unittest.main()
