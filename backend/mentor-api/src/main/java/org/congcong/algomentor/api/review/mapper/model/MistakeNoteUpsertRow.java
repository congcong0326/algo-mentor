package org.congcong.algomentor.api.review.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;

public record MistakeNoteUpsertRow(
    long userId,
    String problemSlug,
    String source,
    JsonNode sourceDetailJson,
    Long originPlanId,
    Integer originPhaseIndex,
    Long originPracticeSessionId
) {
}
