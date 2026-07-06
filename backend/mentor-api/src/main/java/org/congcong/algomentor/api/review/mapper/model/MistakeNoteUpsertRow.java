package org.congcong.algomentor.api.review.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

public record MistakeNoteUpsertRow(
    long userId,
    String problemSlug,
    String source,
    JsonNode sourceDetailJson,
    Long originPlanId,
    Integer originPhaseIndex,
    Long originPracticeSessionId,
    Integer repetitions,
    Integer intervalDays,
    String fsrsState,
    Integer fsrsStep,
    BigDecimal fsrsStability,
    BigDecimal fsrsDifficulty,
    Integer lapses,
    Instant dueAt
) {
}
