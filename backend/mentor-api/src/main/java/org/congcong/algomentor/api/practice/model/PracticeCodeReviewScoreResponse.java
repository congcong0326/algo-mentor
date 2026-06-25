package org.congcong.algomentor.api.practice.model;

import java.math.BigDecimal;

public record PracticeCodeReviewScoreResponse(
    BigDecimal correctness,
    BigDecimal complexity,
    BigDecimal edgeCases,
    BigDecimal codeQuality,
    BigDecimal problemFit,
    BigDecimal total) {
}
