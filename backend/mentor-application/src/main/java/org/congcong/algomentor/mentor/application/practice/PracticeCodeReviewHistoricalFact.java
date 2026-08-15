package org.congcong.algomentor.mentor.application.practice;

import java.time.Instant;

/**
 * 生成当前 Review 摘要所需的历史窄事实；不包含旧代码、Markdown 或评分明细。
 */
public record PracticeCodeReviewHistoricalFact(
    long reviewId,
    boolean passed,
    String primaryFinding,
    Instant createdAt
) {

  public PracticeCodeReviewHistoricalFact {
    if (reviewId < 1 || createdAt == null) {
      throw new IllegalArgumentException("Practice code review historical fact is invalid");
    }
    primaryFinding = primaryFinding == null ? null : primaryFinding.replaceAll("\\s+", " ").trim();
    if (primaryFinding != null && primaryFinding.isEmpty()) {
      primaryFinding = null;
    }
  }
}
