package org.congcong.algomentor.mentor.application.practice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class CodeReviewTurnCapability implements PracticeTurnCapability {

  public static final String CAPABILITY_NAME = PracticeCodeReviewConstants.METADATA_CODE_REVIEW;
  public static final String FAILURE_CODE_RUNTIME_EXCEPTION = "CODE_REVIEW_CAPABILITY_FAILED";

  private final PracticeCodeReviewService reviewService;

  public CodeReviewTurnCapability(PracticeCodeReviewService reviewService) {
    this.reviewService = Objects.requireNonNull(reviewService, "reviewService must not be null");
  }

  @Override
  public String capabilityName() {
    return CAPABILITY_NAME;
  }

  @Override
  public PracticeTurnCapabilityResult afterTurn(PracticeTurnContext context, PracticeTurnClassification classification) {
    if (classification == null || !classification.codeSubmissionCandidate()) {
      return new PracticeTurnCapabilityResult(
          CAPABILITY_NAME,
          PracticeReviewStatus.NOT_CODE_LIKE,
          Map.of("codeSubmissionCandidate", false));
    }
    try {
      PracticeReviewResult reviewResult = reviewService.review(contextWithClassification(context, classification));
      Map<String, Object> metadata = new LinkedHashMap<>(reviewResult.metadata());
      metadata.put("codeSubmissionCandidate", true);
      if (reviewResult.failureCode() != null) {
        metadata.put("failureCode", reviewResult.failureCode());
      }
      return new PracticeTurnCapabilityResult(CAPABILITY_NAME, reviewResult.status(), metadata);
    } catch (RuntimeException exception) {
      return new PracticeTurnCapabilityResult(
          CAPABILITY_NAME,
          PracticeReviewStatus.FAILED,
          Map.of(
              "codeSubmissionCandidate", true,
              "failureCode", FAILURE_CODE_RUNTIME_EXCEPTION,
              "exceptionType", exception.getClass().getSimpleName()));
    }
  }

  private PracticeTurnContext contextWithClassification(
      PracticeTurnContext context,
      PracticeTurnClassification classification
  ) {
    return new PracticeTurnContext(
        context.userId(),
        context.planId(),
        context.phaseIndex(),
        context.problemSlug(),
        context.sessionId(),
        context.userMessageId(),
        context.assistantMessageId(),
        context.agentRunDbId(),
        context.problemFacts(),
        context.learningPlanFacts(),
        classification.extractedCode(),
        classification.originalMessage(),
        context.recentChatSummary(),
        context.locale());
  }
}
