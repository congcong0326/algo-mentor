package org.congcong.algomentor.api.problem.mapper.model;

import java.math.BigDecimal;

public record ProblemCompanySignalUpsertRow(
    String problemSlug,
    Long companyId,
    String role,
    String recencyBucket,
    BigDecimal frequencyScore,
    Integer rank,
    BigDecimal acceptanceRate,
    String sourceName,
    String sourceUrl,
    String sourceCommit,
    String sourceProblemUrl
) {
}
