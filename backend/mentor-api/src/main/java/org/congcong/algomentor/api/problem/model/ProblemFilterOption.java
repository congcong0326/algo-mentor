package org.congcong.algomentor.api.problem.model;

public record ProblemFilterOption(
    String value,
    String label,
    long problemCount
) {
}
