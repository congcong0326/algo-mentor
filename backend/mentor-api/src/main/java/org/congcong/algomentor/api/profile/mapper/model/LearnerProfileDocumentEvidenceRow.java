package org.congcong.algomentor.api.profile.mapper.model;

import java.math.BigDecimal;
import java.time.Instant;

/** 文档 citation 与按需分页共用的安全 evidence 行，禁止加入代码、Markdown 或完整消息。 */
public record LearnerProfileDocumentEvidenceRow(
    long claimRevisionId,
    String sourceType,
    long sourceId,
    Instant occurredAt,
    String reviewRole,
    String messageRole,
    Long reviewSessionId,
    Long planId,
    Integer phaseIndex,
    String problemSlug,
    String problemTitle,
    Integer versionNo,
    BigDecimal totalScore,
    Boolean passed,
    String messageExcerpt
) {
}
