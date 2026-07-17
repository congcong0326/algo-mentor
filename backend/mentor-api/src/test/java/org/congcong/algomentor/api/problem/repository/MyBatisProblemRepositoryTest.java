package org.congcong.algomentor.api.problem.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Array;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.congcong.algomentor.api.problem.mapper.ProblemMapper;
import org.congcong.algomentor.api.problem.mapper.model.ProblemRow;
import org.congcong.algomentor.api.problem.model.NormalizedProblemSeed;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemSeedTag;
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

  @Test
  void upsertProblemWritesCompatibilityArraysFromNormalizedTags() throws Exception {
    ProblemMapper mapper = mock(ProblemMapper.class);
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Array values = mock(Array.class);
    Array labelsEn = mock(Array.class);
    Array labelsZh = mock(Array.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createArrayOf(eq("text"), any(Object[].class)))
        .thenReturn(values, labelsEn, labelsZh);
    MyBatisProblemRepository repository = new MyBatisProblemRepository(mapper, dataSource);
    ProblemSeedRecord rawSeed = new ProblemSeedRecord(
        "two-sum", 1, "1", "Two Sum", "两数之和", null,
        List.of("legacy"), List.of("Legacy"), List.of("旧"),
        "body", "题面", "BILINGUAL", "LEETCODE_COM_CN", null,
        null, null, null, null, null);

    repository.upsertProblem(new NormalizedProblemSeed(rawSeed, List.of(
        new ProblemSeedTag("array", "Array", "数组", 0),
        new ProblemSeedTag("hash-table", "Hash Table", "哈希表", 1))));

    verify(connection).createArrayOf(eq("text"), aryEq(new String[] {"array", "hash-table"}));
    verify(connection).createArrayOf(eq("text"), aryEq(new String[] {"Array", "Hash Table"}));
    verify(connection).createArrayOf(eq("text"), aryEq(new String[] {"数组", "哈希表"}));
    verify(mapper).upsertProblem(any());
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
