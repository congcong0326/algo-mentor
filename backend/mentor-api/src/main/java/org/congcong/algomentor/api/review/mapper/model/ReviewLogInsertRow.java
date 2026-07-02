package org.congcong.algomentor.api.review.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

public record ReviewLogInsertRow(
    long mistakeNoteId,
    long userId,
    String reviewMode,
    String cardVariant,
    JsonNode cardPromptJson,
    String userRecallText,
    String userNoteTransient,
    int grade,
    String gradeSource,
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
