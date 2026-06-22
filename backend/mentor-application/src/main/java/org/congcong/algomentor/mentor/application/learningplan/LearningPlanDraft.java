package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Instant;
import java.util.List;

public record LearningPlanDraft(
    Long id,
    long userId,
    LearningPlanDraftStatus status,
    LearningPlanDraftCommand command,
    List<String> messages,
    List<String> missingFields,
    String assistantMessage,
    LearningPlanDraftPlan draftPlan,
    Long confirmedPlanId,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt
) {

  public LearningPlanDraft {
    messages = messages == null ? List.of() : List.copyOf(messages);
    missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
  }

  public LearningPlanDraft withId(Long nextId) {
    return new LearningPlanDraft(
        nextId,
        userId,
        status,
        command,
        messages,
        missingFields,
        assistantMessage,
        draftPlan,
        confirmedPlanId,
        expiresAt,
        createdAt,
        updatedAt);
  }

  LearningPlanDraft withState(
      LearningPlanDraftStatus nextStatus,
      List<String> nextMissingFields,
      String nextAssistantMessage,
      LearningPlanDraftPlan nextDraftPlan,
      Instant updatedAt) {
    return new LearningPlanDraft(
        id,
        userId,
        nextStatus,
        command,
        messages,
        nextMissingFields,
        nextAssistantMessage,
        nextDraftPlan,
        confirmedPlanId,
        expiresAt,
        createdAt,
        updatedAt);
  }

  LearningPlanDraft withCommandAndMessages(LearningPlanDraftCommand nextCommand, List<String> nextMessages, Instant updatedAt) {
    return new LearningPlanDraft(
        id,
        userId,
        status,
        nextCommand,
        nextMessages,
        missingFields,
        assistantMessage,
        draftPlan,
        confirmedPlanId,
        expiresAt,
        createdAt,
        updatedAt);
  }

  LearningPlanDraft withConfirmedPlanId(long planId, Instant updatedAt) {
    return new LearningPlanDraft(
        id,
        userId,
        LearningPlanDraftStatus.CONFIRMED,
        command,
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
