package org.congcong.algomentor.api.learningplan.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record LearningPlanDraftRevisionRow(
    Long id,
    Long proposalGroupId,
    Long draftId,
    Long userId,
    Integer revisionNo,
    String status,
    String instruction,
    JsonNode basePlanJson,
    JsonNode proposedPlanJson,
    String errorCode,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {
}
