package org.congcong.algomentor.api.problem.mapper.model;

import java.math.BigDecimal;

public record ProblemRow(
    Long id,
    String slug,
    Integer frontendId,
    String frontendDisplayId,
    String titleEn,
    String titleZh,
    String difficulty,
    String tagValuesText,
    String tagLabelsEnText,
    String tagLabelsZhText,
    String contentMarkdownEn,
    String contentMarkdownZh,
    String contentStatus,
    String leetcodeUrl,
    String sampleTestCase,
    String python3Template,
    String sourceCommit,
    String recommendationReasonEn,
    String recommendationReasonZh,
    BigDecimal companyFrequencyScore,
    Long companySignalCount
) {
}
