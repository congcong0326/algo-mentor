package org.congcong.algomentor.api.problem.mapper.model;

public record ProblemTagAssignmentRow(
    String problemSlug,
    String tagValue,
    int ordinal
) {
}
