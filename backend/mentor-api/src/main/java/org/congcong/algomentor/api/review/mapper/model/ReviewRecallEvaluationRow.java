package org.congcong.algomentor.api.review.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ReviewRecallEvaluationRow(
    long id,
    long mistakeNoteId,
    long userId,
    String recallText,
    String transientNote,
    String suggestedRating,
    JsonNode hitPointsJson,
    JsonNode missedPointsJson,
    String gapSummary,
    boolean aiSuggested,
    Instant createdAt
) {
}
