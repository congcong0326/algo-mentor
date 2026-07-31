package org.congcong.algomentor.api.profile.mapper.model;

import java.time.Instant;

/** 文档投影的 ACTIVE claim 行，包含受信 tag 展示名而不包含旧画像正文。 */
public record LearnerProfileDocumentClaimRow(
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
    Instant updatedAt,
    String tagLabelEn,
    String tagLabelZh
) {
}
