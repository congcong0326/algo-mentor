package org.congcong.algomentor.mentor.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

public record ReviewLogEntry(
    long mistakeNoteId,
    long userId,
    ReviewMode reviewMode,
    CardVariant cardVariant,
    JsonNode cardPromptJson,
    String userRecallText,
    String userNoteTransient,
    ReviewGrade grade,
    ReviewRating rating,
    GradeSource gradeSource,
    JsonNode aiJudgmentJson,
    Long practiceCodeReviewId,
    Long recallMessageId,
    int intervalBefore,
    int intervalAfter,
    BigDecimal easeFactorBefore,
    BigDecimal easeFactorAfter,
    Instant reviewedAt
) {
}
