package org.congcong.algomentor.api.practice.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PracticeCodeReviewSummaryResponse(
    long id,
    int versionNo,
    String language,
    BigDecimal totalScore,
    boolean passed,
    Instant createdAt) {
}
