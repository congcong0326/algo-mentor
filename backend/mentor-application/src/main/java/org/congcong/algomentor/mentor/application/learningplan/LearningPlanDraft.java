package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Instant;
import java.util.List;

public record LearningPlanDraft(
    Long id,
    long userId,
    LearningPlanDraftSource source,
    LearningPlanDraftStatus status,
    LearningPlanBrief brief,
    List<String> messages,
    List<String> missingFields,
    String assistantMessage,
    LearningPlanDraftPlan draftPlan,
    Long confirmedPlanId,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt,
    String generationRequestKey,
    String generationRequestFingerprint,
    String generationRunId,
    String generationErrorCode,
    String generationErrorMessage,
    Instant generationStartedAt,
    Instant generationCompletedAt
) {

  public LearningPlanDraft(
      Long id, long userId, LearningPlanDraftStatus status, LearningPlanBrief brief,
      List<String> messages, List<String> missingFields, String assistantMessage,
      LearningPlanDraftPlan draftPlan, Long confirmedPlanId, Instant expiresAt,
      Instant createdAt, Instant updatedAt) {
    this(id, userId, LearningPlanDraftSource.AI_PERSONALIZED, status, brief, messages,
        missingFields, assistantMessage, draftPlan, confirmedPlanId, expiresAt, createdAt, updatedAt,
        null, null, null, null, null, null, null);
  }

  public LearningPlanDraft(
      Long id, long userId, LearningPlanDraftSource source, LearningPlanDraftStatus status,
      LearningPlanBrief brief, List<String> messages, List<String> missingFields,
      String assistantMessage, LearningPlanDraftPlan draftPlan, Long confirmedPlanId,
      Instant expiresAt, Instant createdAt, Instant updatedAt) {
    this(id, userId, source, status, brief, messages, missingFields, assistantMessage, draftPlan,
        confirmedPlanId, expiresAt, createdAt, updatedAt, null, null, null, null, null, null, null);
  }

  public LearningPlanDraft {
    messages = messages == null ? List.of() : List.copyOf(messages);
    missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
  }

  public LearningPlanDraft withId(Long nextId) {
    return new LearningPlanDraft(
        nextId,
        userId,
        source,
        status,
        brief,
        messages,
        missingFields,
        assistantMessage,
        draftPlan,
        confirmedPlanId,
        expiresAt,
        createdAt,
        updatedAt,
        generationRequestKey,
        generationRequestFingerprint,
        generationRunId,
        generationErrorCode,
        generationErrorMessage,
        generationStartedAt,
        generationCompletedAt);
  }

  public LearningPlanDraft withState(
      LearningPlanDraftStatus nextStatus,
      List<String> nextMissingFields,
      String nextAssistantMessage,
      LearningPlanDraftPlan nextDraftPlan,
      Instant updatedAt) {
    return new LearningPlanDraft(
        id,
        userId,
        source,
        nextStatus,
        brief,
        messages,
        nextMissingFields,
        nextAssistantMessage,
        nextDraftPlan,
        confirmedPlanId,
        expiresAt,
        createdAt,
        updatedAt,
        generationRequestKey,
        generationRequestFingerprint,
        generationRunId,
        generationErrorCode,
        generationErrorMessage,
        generationStartedAt,
        generationCompletedAt);
  }

  LearningPlanDraft withBriefAndMessages(LearningPlanBrief nextBrief, List<String> nextMessages, Instant updatedAt) {
    return new LearningPlanDraft(
        id,
        userId,
        source,
        status,
        nextBrief,
        nextMessages,
        missingFields,
        assistantMessage,
        draftPlan,
        confirmedPlanId,
        expiresAt,
        createdAt,
        updatedAt,
        generationRequestKey,
        generationRequestFingerprint,
        generationRunId,
        generationErrorCode,
        generationErrorMessage,
        generationStartedAt,
        generationCompletedAt);
  }

  public LearningPlanDraft withGeneratedRevision(
      LearningPlanBrief nextBrief,
      List<String> nextMessages,
      LearningPlanDraftPlan nextDraftPlan,
      Instant updatedAt
  ) {
    return new LearningPlanDraft(
        id,
        userId,
        source,
        LearningPlanDraftStatus.GENERATED,
        nextBrief,
        nextMessages,
        List.of(),
        "已生成学习计划修订草案。",
        nextDraftPlan,
        confirmedPlanId,
        expiresAt,
        createdAt,
        updatedAt,
        generationRequestKey,
        generationRequestFingerprint,
        generationRunId,
        generationErrorCode,
        generationErrorMessage,
        generationStartedAt,
        generationCompletedAt);
  }

  LearningPlanDraft withConfirmedPlanId(long planId, Instant updatedAt) {
    return new LearningPlanDraft(
        id,
        userId,
        source,
        LearningPlanDraftStatus.CONFIRMED,
        brief,
        messages,
        missingFields,
        assistantMessage,
        draftPlan,
        planId,
        expiresAt,
        createdAt,
        updatedAt,
        generationRequestKey,
        generationRequestFingerprint,
        generationRunId,
        generationErrorCode,
        generationErrorMessage,
        generationStartedAt,
        generationCompletedAt);
  }

  public LearningPlanDraft withGenerationStarted(
      String requestKey,
      String requestFingerprint,
      String runId,
      Instant startedAt
  ) {
    return new LearningPlanDraft(
        id,
        userId,
        source,
        LearningPlanDraftStatus.GENERATING,
        brief,
        messages,
        List.of(),
        "正在生成学习计划草案。",
        null,
        confirmedPlanId,
        expiresAt,
        createdAt,
        startedAt,
        requestKey,
        requestFingerprint,
        runId,
        null,
        null,
        startedAt,
        null);
  }

  public LearningPlanDraft withGenerationRequest(String requestKey, String requestFingerprint) {
    return new LearningPlanDraft(
        id,
        userId,
        source,
        status,
        brief,
        messages,
        missingFields,
        assistantMessage,
        draftPlan,
        confirmedPlanId,
        expiresAt,
        createdAt,
        updatedAt,
        requestKey,
        requestFingerprint,
        null,
        null,
        null,
        null,
        null);
  }

  public LearningPlanDraft withGenerationSucceeded(LearningPlanDraftPlan plan, Instant completedAt) {
    return new LearningPlanDraft(
        id,
        userId,
        source,
        LearningPlanDraftStatus.GENERATED,
        brief,
        messages,
        List.of(),
        "已生成学习计划草案。",
        plan,
        confirmedPlanId,
        expiresAt,
        createdAt,
        completedAt,
        generationRequestKey,
        generationRequestFingerprint,
        generationRunId,
        null,
        null,
        generationStartedAt,
        completedAt);
  }

  public LearningPlanDraft withGenerationFailed(String code, String message, Instant completedAt) {
    return new LearningPlanDraft(
        id,
        userId,
        source,
        LearningPlanDraftStatus.GENERATION_FAILED,
        brief,
        messages,
        List.of(),
        message,
        null,
        confirmedPlanId,
        expiresAt,
        createdAt,
        completedAt,
        generationRequestKey,
        generationRequestFingerprint,
        generationRunId,
        code,
        message,
        generationStartedAt,
        completedAt);
  }
}
