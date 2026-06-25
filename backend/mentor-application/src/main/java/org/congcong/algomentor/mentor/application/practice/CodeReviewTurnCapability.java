package org.congcong.algomentor.mentor.application.practice;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class CodeReviewTurnCapability implements PracticeTurnCapability {

  public static final String CAPABILITY_NAME = PracticeCodeReviewConstants.METADATA_CODE_REVIEW;
  public static final String FAILURE_CODE_RUNTIME_EXCEPTION = "CODE_REVIEW_CAPABILITY_FAILED";

  private final PracticeCodeReviewService reviewService;
  private final PracticeCodeReviewMetrics metrics;

  public CodeReviewTurnCapability(PracticeCodeReviewService reviewService) {
    this(reviewService, PracticeCodeReviewMetrics.NOOP);
  }

  public CodeReviewTurnCapability(PracticeCodeReviewService reviewService, PracticeCodeReviewMetrics metrics) {
    this.reviewService = Objects.requireNonNull(reviewService, "reviewService must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
  }

  @Override
  public String capabilityName() {
    return CAPABILITY_NAME;
  }

  @Override
  public PracticeTurnCapabilityResult afterTurn(PracticeTurnContext context, PracticeTurnClassification classification) {
    Instant startedAt = Instant.now();
    if (classification == null || !classification.codeSubmissionCandidate()) {
      PracticeTurnCapabilityResult result = new PracticeTurnCapabilityResult(
          CAPABILITY_NAME,
          PracticeReviewStatus.NOT_CODE_LIKE,
          Map.of("codeSubmissionCandidate", false));
      record(false, result, startedAt);
      return result;
    }
    try {
      PracticeTurnContext reviewContext = contextWithClassification(context, classification);
      PracticeReviewResult reviewResult = classification.idempotentReplay()
          ? reviewService.replay(reviewContext)
          : reviewService.review(reviewContext);
      Map<String, Object> metadata = new LinkedHashMap<>(reviewResult.metadata());
      metadata.put("codeSubmissionCandidate", true);
      if (reviewResult.failureCode() != null) {
        metadata.put("failureCode", reviewResult.failureCode());
      }
      PracticeTurnCapabilityResult result = new PracticeTurnCapabilityResult(CAPABILITY_NAME, reviewResult.status(), metadata);
      record(true, result, startedAt);
      return result;
    } catch (RuntimeException exception) {
      PracticeTurnCapabilityResult result = new PracticeTurnCapabilityResult(
          CAPABILITY_NAME,
          PracticeReviewStatus.FAILED,
          Map.of(
              "codeSubmissionCandidate", true,
              "failureCode", FAILURE_CODE_RUNTIME_EXCEPTION,
              "exceptionType", exception.getClass().getSimpleName()));
      record(true, result, startedAt);
      return result;
    }
  }

  private void record(boolean codeSubmissionCandidate, PracticeTurnCapabilityResult result, Instant startedAt) {
    Object failureCode = result.metadata().get("failureCode");
    metrics.recordCapability(
        codeSubmissionCandidate,
        result.status(),
        failureCode instanceof String text ? text : null,
        Duration.between(startedAt, Instant.now()));
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
