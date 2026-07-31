package org.congcong.algomentor.api.profile.mapper.model;

import java.time.Instant;

public record LearnerMemoryReviewEvidenceRow(
    long claimRevisionId,
    long reviewId,
    String evidenceRole,
    int sequenceNo,
    Instant createdAt
) {
}
