package org.congcong.algomentor.api.problem.model;

public record ProblemCategoryFilterOption(
    String slug,
    String name,
    long problemCount
) {
}
