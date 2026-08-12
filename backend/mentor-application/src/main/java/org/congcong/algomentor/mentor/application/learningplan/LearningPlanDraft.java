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
    Instant updatedAt
) {

  public LearningPlanDraft(
      Long id, long userId, LearningPlanDraftStatus status, LearningPlanBrief brief,
      List<String> messages, List<String> missingFields, String assistantMessage,
      LearningPlanDraftPlan draftPlan, Long confirmedPlanId, Instant expiresAt,
      Instant createdAt, Instant updatedAt) {
    this(id, userId, LearningPlanDraftSource.AI_PERSONALIZED, status, brief, messages,
        missingFields, assistantMessage, draftPlan, confirmedPlanId, expiresAt, createdAt, updatedAt);
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
        updatedAt);
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
        updatedAt);
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
        updatedAt);
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
        updatedAt);
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
        updatedAt);
  }
}
