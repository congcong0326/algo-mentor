package org.congcong.algomentor.api.problem.mapper.model;

public record ProblemCategoryUpsertRow(
    String slug,
    String nameEn,
    String nameZh,
    String source,
    String sourceSnapshot
) {
}
