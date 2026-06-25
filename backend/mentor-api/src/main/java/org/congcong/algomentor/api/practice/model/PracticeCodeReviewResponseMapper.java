package org.congcong.algomentor.api.practice.model;

import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewEvidence;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewHistory;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewSummary;
import org.congcong.algomentor.mentor.application.practice.PracticeCompletionGate;

public final class PracticeCodeReviewResponseMapper {

  private PracticeCodeReviewResponseMapper() {
  }

  public static PracticeCodeReviewHistoryResponse toHistoryResponse(PracticeCodeReviewHistory history) {
    return new PracticeCodeReviewHistoryResponse(
        toSummaryResponse(history.latestReview()),
        history.reviews().stream()
            .map(PracticeCodeReviewResponseMapper::toSummaryResponse)
            .toList(),
        toCompletionGateResponse(history.completionGate()));
  }

  public static PracticeCodeReviewDetailResponse toDetailResponse(PracticeCodeReview review) {
    return new PracticeCodeReviewDetailResponse(
        review.id(),
        review.planId(),
        review.phaseIndex(),
        review.problemSlug(),
        review.sessionId(),
        review.versionNo(),
        review.userMessageId(),
        review.assistantMessageId(),
        review.agentRunDbId(),
        review.rawCode(),
        review.normalizedCode(),
        review.language(),
        review.evidence().stream()
            .map(PracticeCodeReviewResponseMapper::toEvidenceResponse)
            .toList(),
        review.contextSummary(),
        toScoreResponse(review.score()),
        review.passed(),
        review.deductionReasons(),
        review.improvementSuggestions(),
        review.reviewMarkdown(),
        review.createdAt());
  }

  public static PracticeCodeReviewSummaryResponse toSummaryResponse(PracticeCodeReviewSummary summary) {
    if (summary == null) {
      return null;
    }
    return new PracticeCodeReviewSummaryResponse(
        summary.id(),
        summary.versionNo(),
        summary.language(),
        summary.totalScore(),
        summary.passed(),
        summary.createdAt());
  }

  public static PracticeCompletionGateResponse toCompletionGateResponse(PracticeCompletionGate gate) {
    return new PracticeCompletionGateResponse(
        gate.canComplete(),
        gate.reasonCode().name(),
        gate.message(),
        gate.latestScore().orElse(null),
        gate.passScore());
  }

  private static PracticeCodeReviewEvidenceResponse toEvidenceResponse(PracticeCodeReviewEvidence evidence) {
    return new PracticeCodeReviewEvidenceResponse(evidence.type(), evidence.value());
  }

  private static PracticeCodeReviewScoreResponse toScoreResponse(PracticeCodeReviewScore score) {
    return new PracticeCodeReviewScoreResponse(
        score.correctness(),
        score.complexity(),
        score.edgeCases(),
        score.codeQuality(),
        score.problemFit(),
        score.total());
  }
}
