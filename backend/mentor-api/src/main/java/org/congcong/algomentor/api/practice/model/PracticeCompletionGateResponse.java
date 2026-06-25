package org.congcong.algomentor.api.practice.model;

import java.math.BigDecimal;

public record PracticeCompletionGateResponse(
    boolean canComplete,
    String reasonCode,
    String message,
    BigDecimal latestScore,
    BigDecimal passScore) {
}
