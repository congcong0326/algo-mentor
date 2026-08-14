package org.congcong.algomentor.api.problem.mapper.model;

public record ProblemCodeTemplateUpsertRow(
    String problemSlug,
    String languageSlug,
    String languageLabel,
    String code,
    String sourceSite,
    String sourceSnapshot
) {
}
