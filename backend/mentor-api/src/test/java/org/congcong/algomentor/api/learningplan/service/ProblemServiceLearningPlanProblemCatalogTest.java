package org.congcong.algomentor.api.learningplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemDifficulty;
import org.congcong.algomentor.api.problem.model.ProblemFilterOption;
import org.congcong.algomentor.api.problem.model.ProblemFilters;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemTag;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.junit.jupiter.api.Test;

class ProblemServiceLearningPlanProblemCatalogTest {

  @Test
  void findBySlugMapsLocaleSpecificRecommendationReason() {
    ProblemService problemService = mock(ProblemService.class);
    when(problemService.findProblemBySlug("two-sum", ProblemLocale.ZH_CN))
        .thenReturn(Optional.of(problem("两数之和", "练习哈希表查找。")));
    when(problemService.findProblemBySlug("two-sum", ProblemLocale.EN_US))
        .thenReturn(Optional.of(problem("Two Sum", "Practice hash-table lookups.")));
    ProblemServiceLearningPlanProblemCatalog catalog = new ProblemServiceLearningPlanProblemCatalog(problemService);

    var chinese = catalog.findBySlug("two-sum", "zh-CN").orElseThrow();
    var english = catalog.findBySlug("two-sum", "en-US").orElseThrow();

    assertThat(chinese.recommendationReason()).isEqualTo("练习哈希表查找。");
    assertThat(english.recommendationReason()).isEqualTo("Practice hash-table lookups.");
    assertThat(chinese.title()).isEqualTo("Two Sum");
    assertThat(chinese.titleCn()).isEqualTo("两数之和");
    assertThat(english.title()).isEqualTo("Two Sum");
    assertThat(english.titleCn()).isEqualTo("两数之和");
    assertThat(chinese.tags()).containsExactly("array");
  }

  @Test
  void normalizesLocalizedTagLabelToStableValue() {
    ProblemService problemService = mock(ProblemService.class);
    when(problemService.findProblemFilters(ProblemLocale.ZH_CN)).thenReturn(new ProblemFilters(
        1,
        List.of(),
        List.of(new ProblemFilterOption("binary-search", "二分查找", 1)),
        List.of(),
        List.of(),
        List.of(),
        List.of()));
    ProblemServiceLearningPlanProblemCatalog catalog = new ProblemServiceLearningPlanProblemCatalog(problemService);

    assertThat(catalog.findCanonicalTagValue("二分查找", "zh-CN"))
        .contains("binary-search");
  }

  private ProblemDetail problem(String title, String recommendationReason) {
    return new ProblemDetail(
        "two-sum",
        1,
        "1",
        title,
        ProblemDifficulty.EASY,
        List.of(new ProblemTag("array", "Array")),
        "# Two Sum",
        "BILINGUAL",
        "https://leetcode.com/problems/two-sum/",
        "[2,7,11,15]\\n9",
        "class Solution:\\n    pass",
        "abc123",
        recommendationReason);
  }
}
