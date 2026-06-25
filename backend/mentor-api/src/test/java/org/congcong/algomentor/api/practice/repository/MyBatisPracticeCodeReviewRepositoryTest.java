package org.congcong.algomentor.api.practice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewInsertRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewSessionLockRow;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewDraft;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewEvidence;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.junit.jupiter.api.Test;

class MyBatisPracticeCodeReviewRepositoryTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void mapsJsonbRowsToDomain() {
    PracticeCodeReviewMapper mapper = mock(PracticeCodeReviewMapper.class);
    when(mapper.findLatest(7L, 50L)).thenReturn(fullRow());
    MyBatisPracticeCodeReviewRepository repository = new MyBatisPracticeCodeReviewRepository(mapper, objectMapper);

    PracticeCodeReview review = repository.findLatest(7L, 50L).orElseThrow();

    assertThat(review.evidence()).contains(new PracticeCodeReviewEvidence("ENTRY_FUNCTION", "twoSum"));
    assertThat(review.deductionReasons()).containsExactly("边界条件不足");
    assertThat(review.score().total()).isEqualByComparingTo("7.0");
  }

  @Test
  void findByIdReturnsEmptyWhenMapperReturnsNull() {
    PracticeCodeReviewMapper mapper = mock(PracticeCodeReviewMapper.class);
    when(mapper.findById(7L, 50L, 90L)).thenReturn(null);
    MyBatisPracticeCodeReviewRepository repository = new MyBatisPracticeCodeReviewRepository(mapper, objectMapper);

    Optional<PracticeCodeReview> review = repository.findById(7L, 50L, 90L);

    assertThat(review).isEmpty();
  }

  @Test
  void saveReturnsInsertedRow() {
    PracticeCodeReviewMapper mapper = mock(PracticeCodeReviewMapper.class);
    when(mapper.lockSessionForReviewInsert(7L, 50L)).thenReturn(lockRow());
    when(mapper.insert(argThat(row ->
        row.userId() == 7L
            && row.sessionId() == 50L
            && row.planId() == 12L
            && row.phaseIndex() == 1
            && row.problemSlug().equals("two-sum")
            && row.agentRunDbId().equals(101L)
            && row.detectionEvidenceJson().equals(objectMapper.valueToTree(List.of(
                new PracticeCodeReviewEvidence("ENTRY_FUNCTION", "twoSum")))))))
        .thenReturn(fullRow());
    MyBatisPracticeCodeReviewRepository repository = new MyBatisPracticeCodeReviewRepository(mapper, objectMapper);

    PracticeCodeReview review = repository.save(draft());

    assertThat(review.id()).isEqualTo(90L);
    assertThat(review.agentRunDbId()).isEqualTo(101L);
    assertThat(review.improvementSuggestions()).containsExactly("补充空数组用例");
  }

  @Test
  void saveUsesEmptyStringsForNullableTextColumns() {
    PracticeCodeReviewMapper mapper = mock(PracticeCodeReviewMapper.class);
    when(mapper.lockSessionForReviewInsert(7L, 50L)).thenReturn(lockRow());
    when(mapper.insert(argThat(row -> row.contextSummary().isEmpty() && row.reviewMarkdown().isEmpty())))
        .thenReturn(fullRow());
    MyBatisPracticeCodeReviewRepository repository = new MyBatisPracticeCodeReviewRepository(mapper, objectMapper);

    PracticeCodeReview review = repository.save(nullableTextDraft());

    assertThat(review.id()).isEqualTo(90L);
  }

  @Test
  void saveThrowsClearExceptionWhenMapperInsertReturnsNull() {
    PracticeCodeReviewMapper mapper = mock(PracticeCodeReviewMapper.class);
    when(mapper.lockSessionForReviewInsert(7L, 50L)).thenReturn(lockRow());
    when(mapper.insert(argThat(row -> row.sessionId() == 50L))).thenReturn(null);
    MyBatisPracticeCodeReviewRepository repository = new MyBatisPracticeCodeReviewRepository(mapper, objectMapper);

    assertThatThrownBy(() -> repository.save(draft()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Practice code review session was not found or is not writable");
  }

  @Test
  void saveThrowsClearExceptionWhenSessionCannotBeLocked() {
    PracticeCodeReviewMapper mapper = mock(PracticeCodeReviewMapper.class);
    when(mapper.lockSessionForReviewInsert(7L, 50L)).thenReturn(null);
    MyBatisPracticeCodeReviewRepository repository = new MyBatisPracticeCodeReviewRepository(mapper, objectMapper);

    assertThatThrownBy(() -> repository.save(draft()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Practice code review session was not found or is not writable");
  }

  private PracticeCodeReviewDraft draft() {
    return new PracticeCodeReviewDraft(
        7L,
        12L,
        1,
        "two-sum",
        50L,
        700L,
        701L,
        101L,
        "class Solution {}",
        "class Solution {}",
        "java",
        List.of(new PracticeCodeReviewEvidence("ENTRY_FUNCTION", "twoSum")),
        "使用哈希表。",
        score(),
        true,
        List.of("边界条件不足"),
        List.of("补充空数组用例"),
        "整体可通过。");
  }

  private PracticeCodeReviewDraft nullableTextDraft() {
    return new PracticeCodeReviewDraft(
        7L,
        12L,
        1,
        "two-sum",
        50L,
        700L,
        701L,
        101L,
        "class Solution {}",
        "class Solution {}",
        "java",
        List.of(new PracticeCodeReviewEvidence("ENTRY_FUNCTION", "twoSum")),
        null,
        score(),
        true,
        List.of("边界条件不足"),
        List.of("补充空数组用例"),
        null);
  }

  private PracticeCodeReviewSessionLockRow lockRow() {
    return new PracticeCodeReviewSessionLockRow(50L, 12L, 1, "two-sum");
  }

  private PracticeCodeReviewRow fullRow() {
    return new PracticeCodeReviewRow(
        90L,
        7L,
        12L,
        1,
        "two-sum",
        50L,
        2,
        700L,
        701L,
        101L,
        "class Solution {}",
        "class Solution {}",
        "java",
        objectMapper.valueToTree(List.of(new PracticeCodeReviewEvidence("ENTRY_FUNCTION", "twoSum"))),
        "使用哈希表。",
        new BigDecimal("7.0"),
        new BigDecimal("3.0"),
        new BigDecimal("1.5"),
        new BigDecimal("1.0"),
        new BigDecimal("0.8"),
        new BigDecimal("0.7"),
        true,
        objectMapper.valueToTree(List.of("边界条件不足")),
        objectMapper.valueToTree(List.of("补充空数组用例")),
        "整体可通过。",
        CREATED_AT);
  }

  private PracticeCodeReviewScore score() {
    return new PracticeCodeReviewScore(
        new BigDecimal("3.0"),
        new BigDecimal("1.5"),
        new BigDecimal("1.0"),
        new BigDecimal("0.8"),
        new BigDecimal("0.7"),
        new BigDecimal("7.0"));
  }
}
