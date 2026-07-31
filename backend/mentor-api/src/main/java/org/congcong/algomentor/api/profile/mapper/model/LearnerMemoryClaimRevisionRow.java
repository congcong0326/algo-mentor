package org.congcong.algomentor.api.profile.mapper.model;

import java.time.Instant;

public record LearnerMemoryClaimRevisionRow(
    long id,
    String claimKey,
    long userId,
    String entryKind,
    String dimension,
    Long tagId,
    int revisionNo,
    String status,
    String claimText,
    String claimTextHash,
    String originType,
    String evidencePattern,
    String evidenceGrade,
    String decisionReason,
    long updateRunId,
    Long supersedesRevisionId,
    Instant validFrom,
    Instant validTo,
    Instant createdAt,
    Instant updatedAt
) {
}
