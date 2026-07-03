package org.congcong.algomentor.api.problem.model;

import java.math.BigDecimal;

public record ProblemCompanySeedRecord(
    String companySlug,
    String companyName,
    String companyMarket,
    String role,
    String problemSlug,
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
