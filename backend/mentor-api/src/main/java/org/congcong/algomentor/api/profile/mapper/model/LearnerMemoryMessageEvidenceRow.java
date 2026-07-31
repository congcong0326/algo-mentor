package org.congcong.algomentor.api.profile.mapper.model;

import java.time.Instant;

public record LearnerMemoryMessageEvidenceRow(
    long claimRevisionId,
    long messageId,
    String evidenceRole,
    int sequenceNo,
    Instant createdAt
) {
}
