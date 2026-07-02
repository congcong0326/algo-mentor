package org.congcong.algomentor.mentor.application.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleBasedCardComposerTest {

  private final RuleBasedCardComposer composer = new RuleBasedCardComposer();

  @Test
  void easyMasteredKeepsKeyStepAndComplexityOnly() {
    ReviewCard card = composer.compose(note(Map.of("difficulty", "EASY"), ReviewGrade.MASTERED, 0));

    assertThat(card.cardVariant()).isEqualTo(CardVariant.RULE_BASED);
    assertThat(card.prompts()).extracting(ReviewCardPrompt::key)
        .containsExactly("key_step", "complexity");
    assertThat(card.scaffold()).isNull();
  }

  @Test
  void hardProblemGetsFullScaffold() {
    ReviewCard card = composer.compose(note(Map.of("difficulty", "HARD"), ReviewGrade.BARELY, 0));

    assertThat(card.prompts()).hasSize(4);
    assertThat(card.scaffold()).isNotNull();
    assertThat(card.scaffold().maxInputChars()).isEqualTo(400);
  }

  @Test
  void repeatedLapsesGetScaffold() {
    ReviewCard card = composer.compose(note(Map.of("difficulty", "MEDIUM"), ReviewGrade.BARELY, 2));

    assertThat(card.prompts()).hasSize(4);
    assertThat(card.scaffold()).isNotNull();
  }

  @Test
  void usesProblemMetadataFromSourceDetail() {
    ReviewCard card = composer.compose(note(Map.of(
        "difficulty", "MEDIUM",
        "titleCn", "两数之和",
        "statementSummary", "给定整数数组和目标值，返回两数下标。"), ReviewGrade.BARELY, 0));

    assertThat(card.problemRef().titleCn()).isEqualTo("两数之和");
    assertThat(card.problemStatement().summary()).isEqualTo("给定整数数组和目标值，返回两数下标。");
  }

  private MistakeNote note(Map<String, Object> sourceDetail, ReviewGrade lastGrade, int lapses) {
    return new MistakeNote(
        1L,
        7L,
        "two-sum",
        MistakeSource.REVIEW_FAILED,
        sourceDetail,
        null,
        null,
        null,
        new SchedulingState(0, new BigDecimal("2.50"), 0, MasteryState.NEW, lapses),
        Instant.parse("2026-07-02T00:00:00Z"),
        null,
        lastGrade,
        false,
        "",
        null,
        Instant.parse("2026-07-02T00:00:00Z"),
        Instant.parse("2026-07-02T00:00:00Z"));
  }
}
