package org.congcong.algomentor.api.review.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record UserProblemNoteRow(
    long id,
    long userId,
    String problemSlug,
    JsonNode outlineJson,
    String noteMarkdown,
    long revision,
    Instant createdAt,
    Instant updatedAt
) {
}
