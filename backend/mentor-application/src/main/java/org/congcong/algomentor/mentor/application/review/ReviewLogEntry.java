package org.congcong.algomentor.mentor.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ReviewLogEntry(
    long mistakeNoteId,
    long userId,
    ReviewMode reviewMode,
    CardVariant cardVariant,
    JsonNode cardPromptJson,
    String userRecallText,
    String userNoteTransient,
    ReviewRating rating,
    RatingSource ratingSource,
    JsonNode aiJudgmentJson,
    Long practiceCodeReviewId,
    Long recallMessageId,
    int intervalBefore,
    int intervalAfter,
    Instant reviewedAt
) {
}
