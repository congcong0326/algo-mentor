package org.congcong.algomentor.api.problem.mapper.model;

public record ProblemCategoryItemUpsertRow(
    String problemSlug,
    String categorySlug,
    String source,
    String sourceSnapshot
) {
}
