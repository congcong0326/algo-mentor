package org.congcong.algomentor.api.problem.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.sql.DataSource;
import org.congcong.algomentor.api.problem.mapper.ProblemMapper;
import org.congcong.algomentor.api.problem.mapper.model.ProblemRow;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.junit.jupiter.api.Test;

class MyBatisProblemRepositoryTest {

  @Test
  void findProblemBySlugUsesLocalePreferredRecommendationReason() {
    ProblemMapper mapper = mock(ProblemMapper.class);
    when(mapper.findProblemBySlug("two-sum")).thenReturn(problemRow(
        "Practice hash-table lookups.",
        "练习哈希表查找。"));
    MyBatisProblemRepository repository = new MyBatisProblemRepository(mapper, mock(DataSource.class));

    String chineseReason = repository.findProblemBySlug("two-sum", ProblemLocale.ZH_CN)
        .orElseThrow()
        .recommendationReason();
    String englishReason = repository.findProblemBySlug("two-sum", ProblemLocale.EN_US)
        .orElseThrow()
        .recommendationReason();

    assertThat(chineseReason).isEqualTo("练习哈希表查找。");
    assertThat(englishReason).isEqualTo("Practice hash-table lookups.");
  }

  @Test
  void findProblemBySlugFallsBackToTheOtherRecommendationReasonLanguage() {
    ProblemMapper mapper = mock(ProblemMapper.class);
    when(mapper.findProblemBySlug("english-only")).thenReturn(problemRow("Practice hash-table lookups.", ""));
    when(mapper.findProblemBySlug("chinese-only")).thenReturn(problemRow("", "练习哈希表查找。"));
    MyBatisProblemRepository repository = new MyBatisProblemRepository(mapper, mock(DataSource.class));

    String chineseReason = repository.findProblemBySlug("english-only", ProblemLocale.ZH_CN)
        .orElseThrow()
        .recommendationReason();
    String englishReason = repository.findProblemBySlug("chinese-only", ProblemLocale.EN_US)
        .orElseThrow()
        .recommendationReason();

    assertThat(chineseReason).isEqualTo("Practice hash-table lookups.");
    assertThat(englishReason).isEqualTo("练习哈希表查找。");
  }

  private ProblemRow problemRow(String recommendationReasonEn, String recommendationReasonZh) {
    return new ProblemRow(
        1L,
        "two-sum",
        1,
        "1",
        "Two Sum",
        "两数之和",
        "EASY",
        "array",
        "Array",
        "数组",
        "# Two Sum",
        "# 两数之和",
        "BILINGUAL",
        "https://leetcode.com/problems/two-sum/",
        "[2,7,11,15]\\n9",
        "class Solution:\\n    pass",
        "abc123",
        recommendationReasonEn,
        recommendationReasonZh,
        null,
        0L);
  }
}
