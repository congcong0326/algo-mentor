package org.congcong.algomentor.api.review.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ProblemReviewAttemptRow(
    long id,
    long reviewCardId,
    long userId,
    String clientAttemptId,
    String rating,
    JsonNode schedulingBeforeJson,
    JsonNode schedulingAfterJson,
    Instant reviewedAt
) {
}
