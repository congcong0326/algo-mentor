package org.congcong.algomentor.api.review.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ReviewLogInsertRow(
    long mistakeNoteId,
    long userId,
    String reviewMode,
    String cardVariant,
    JsonNode cardPromptJson,
    String userRecallText,
    String userNoteTransient,
    String rating,
    String ratingSource,
    JsonNode aiJudgmentJson,
    Long practiceCodeReviewId,
    Long recallMessageId,
    int intervalBefore,
    int intervalAfter,
    Instant reviewedAt
) {
}
