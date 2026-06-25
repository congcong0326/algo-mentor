package org.congcong.algomentor.api.practice.model;

import java.util.List;

public record PracticeCodeReviewHistoryResponse(
    PracticeCodeReviewSummaryResponse latestReview,
    List<PracticeCodeReviewSummaryResponse> reviews,
    PracticeCompletionGateResponse completionGate) {
}
