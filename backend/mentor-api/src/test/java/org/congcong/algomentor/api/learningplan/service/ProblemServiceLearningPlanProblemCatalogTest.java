package org.congcong.algomentor.api.learningplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemDifficulty;
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

    String chineseReason = catalog.findBySlug("two-sum", "zh-CN")
        .orElseThrow()
        .recommendationReason();
    String englishReason = catalog.findBySlug("two-sum", "en-US")
        .orElseThrow()
        .recommendationReason();

    assertThat(chineseReason).isEqualTo("练习哈希表查找。");
    assertThat(englishReason).isEqualTo("Practice hash-table lookups.");
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
