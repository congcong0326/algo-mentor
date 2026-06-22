package org.congcong.algomentor.api.learningplan.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record LearningPlanRow(
    Long id,
    Long userId,
    String status,
    String title,
    JsonNode planJson,
    Instant createdAt,
    Instant updatedAt
) {
}
