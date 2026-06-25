package org.congcong.algomentor.api.practice.mapper.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PracticeCodeReviewSummaryRow(
    long id,
    int versionNo,
    String language,
    BigDecimal totalScore,
    boolean passed,
    Instant createdAt
) {
}
