package org.congcong.algomentor.api.learningplan.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record LearningPlanExtensionRevisionRow(
    Long id,
    Long proposalGroupId,
    Long planId,
    Long userId,
    Integer revisionNo,
    String status,
    String instruction,
    JsonNode basePlanJson,
    JsonNode progressSnapshotJson,
    Integer baseMaxPhaseIndex,
    JsonNode previousExtensionJson,
    JsonNode proposedExtensionJson,
    Instant appliedAt,
    String errorCode,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {
}
