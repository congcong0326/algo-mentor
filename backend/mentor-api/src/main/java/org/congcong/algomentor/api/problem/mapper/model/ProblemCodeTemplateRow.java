package org.congcong.algomentor.api.problem.mapper.model;

public record ProblemCodeTemplateRow(
    String languageSlug,
    String languageLabel,
    String code,
    String sourceSite,
    String sourceSnapshot
) {
}
