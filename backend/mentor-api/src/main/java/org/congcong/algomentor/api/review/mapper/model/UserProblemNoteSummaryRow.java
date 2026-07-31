package org.congcong.algomentor.api.review.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record UserProblemNoteSummaryRow(
    Long id,
    long userId,
    String problemSlug,
    JsonNode outlineJson,
    boolean hasNoteMarkdown,
    long revision,
    Instant createdAt,
    Instant updatedAt
) {
}
