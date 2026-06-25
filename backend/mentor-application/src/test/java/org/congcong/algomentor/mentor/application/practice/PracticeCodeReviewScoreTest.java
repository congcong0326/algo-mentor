package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewScoreTest {

  @Test
  void draftRequiresPrimitiveUserMessageId() {
    assertThat(PracticeCodeReviewDraft.class.getRecordComponents())
        .filteredOn(component -> component.getName().equals("userMessageId"))
        .singleElement()
        .extracting(RecordComponent::getType)
        .isEqualTo(long.class);
  }

  @Test
  void draftRejectsNonPositiveUserMessageId() {
    assertThatThrownBy(() -> draft(0L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("user message id must be positive");
    assertThatThrownBy(() -> draft(-1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("user message id must be positive");
  }

  @Test
  void rejectsScoresAboveDimensionLimits() {
    assertThatThrownBy(() -> score("4.1", "2.0", "2.0", "1.0", "1.0", "10.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("correctness score must be between 0 and 4");
    assertThatThrownBy(() -> score("4.0", "2.1", "2.0", "1.0", "1.0", "10.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("complexity score must be between 0 and 2");
    assertThatThrownBy(() -> score("4.0", "2.0", "2.1", "1.0", "1.0", "10.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("edge cases score must be between 0 and 2");
    assertThatThrownBy(() -> score("4.0", "2.0", "2.0", "1.1", "1.0", "10.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("code quality score must be between 0 and 1");
    assertThatThrownBy(() -> score("4.0", "2.0", "2.0", "1.0", "1.1", "10.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("problem fit score must be between 0 and 1");
  }

  private PracticeCodeReviewScore score(
      String correctness,
      String complexity,
      String edgeCases,
      String codeQuality,
      String problemFit,
      String total
  ) {
    return new PracticeCodeReviewScore(
        new BigDecimal(correctness),
        new BigDecimal(complexity),
        new BigDecimal(edgeCases),
        new BigDecimal(codeQuality),
        new BigDecimal(problemFit),
        new BigDecimal(total));
  }

  private PracticeCodeReviewDraft draft(long userMessageId) {
    return new PracticeCodeReviewDraft(
        7L,
        12L,
        1,
        "two-sum",
        50L,
        userMessageId,
        701L,
        101L,
        "class Solution {}",
        "class Solution {}",
        "java",
        java.util.List.of(new PracticeCodeReviewEvidence("ENTRY_FUNCTION", "twoSum")),
        "使用哈希表。",
        score("3.0", "1.5", "1.0", "0.8", "0.7", "7.0"),
        true,
        java.util.List.of("边界条件不足"),
        java.util.List.of("补充空数组用例"),
        "整体可通过。");
  }
}
