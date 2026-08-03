package org.congcong.algomentor.mentor.application.learningplan.personalization;

import java.math.BigDecimal;

/** 能力标签的聚合摘要，不包含题目或 Review 明细。 */
public record LearningPlanAbilityTagSummary(
    String tag,
    String label,
    long reviewedProblemCount,
    BigDecimal rawAverageScore,
    BigDecimal abilityScore
) {

  public LearningPlanAbilityTagSummary {
    tag = requireText(tag, "tag");
    label = requireText(label, "label");
    if (reviewedProblemCount < 0) {
      throw new IllegalArgumentException("reviewed problem count must not be negative");
    }
    if (rawAverageScore == null || abilityScore == null) {
      throw new IllegalArgumentException("ability scores must not be null");
    }
  }

  private static String requireText(String value, String fieldName) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return normalized;
  }
}
