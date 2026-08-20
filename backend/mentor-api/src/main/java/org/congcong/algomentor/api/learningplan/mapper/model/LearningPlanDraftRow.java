package org.congcong.algomentor.api.learningplan.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record LearningPlanDraftRow(
    Long id,
    Long userId,
    String draftSource,
    String status,
    JsonNode commandJson,
    JsonNode messagesJson,
    JsonNode missingFieldsJson,
    String assistantMessage,
    JsonNode draftPlanJson,
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
  public LearningPlanDraftRow(
      Long id, Long userId, String status, JsonNode commandJson, JsonNode messagesJson,
      JsonNode missingFieldsJson, String assistantMessage, JsonNode draftPlanJson,
      Long confirmedPlanId, Instant expiresAt, Instant createdAt, Instant updatedAt) {
    this(id, userId, "AI_PERSONALIZED", status, commandJson, messagesJson, missingFieldsJson,
        assistantMessage, draftPlanJson, confirmedPlanId, expiresAt, createdAt, updatedAt,
        null, null, null, null, null, null, null);
  }

  public LearningPlanDraftRow(
      Long id, Long userId, String draftSource, String status, JsonNode commandJson, JsonNode messagesJson,
      JsonNode missingFieldsJson, String assistantMessage, JsonNode draftPlanJson, Long confirmedPlanId,
      Instant expiresAt, Instant createdAt, Instant updatedAt) {
    this(id, userId, draftSource, status, commandJson, messagesJson, missingFieldsJson, assistantMessage,
        draftPlanJson, confirmedPlanId, expiresAt, createdAt, updatedAt,
        null, null, null, null, null, null, null);
  }
}
