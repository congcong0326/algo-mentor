package org.congcong.algomentor.api.practice.model;

import java.util.List;

public record PracticeSessionResponse(
    PracticeSessionSummaryResponse session,
    PracticeProblemSummaryResponse problem,
    List<PracticeMessageResponse> messages,
    PracticeActiveRunResponse activeRun,
    PracticeCodeReviewSummaryResponse latestReview,
    PracticeCompletionGateResponse completionGate) {
}
