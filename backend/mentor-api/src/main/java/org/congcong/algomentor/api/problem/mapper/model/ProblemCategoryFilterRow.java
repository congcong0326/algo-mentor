package org.congcong.algomentor.api.problem.mapper.model;

public record ProblemCategoryFilterRow(
    String slug,
    String name,
    Long problemCount
) {
}
