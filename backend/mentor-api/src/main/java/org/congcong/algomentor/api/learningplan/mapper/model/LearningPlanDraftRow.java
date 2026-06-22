package org.congcong.algomentor.api.learningplan.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record LearningPlanDraftRow(
    Long id,
    Long userId,
    String status,
    JsonNode commandJson,
    JsonNode messagesJson,
    JsonNode missingFieldsJson,
    String assistantMessage,
    JsonNode draftPlanJson,
    Long confirmedPlanId,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt
) {
}
