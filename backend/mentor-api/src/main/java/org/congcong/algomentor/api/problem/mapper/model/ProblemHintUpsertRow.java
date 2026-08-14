package org.congcong.algomentor.api.problem.mapper.model;

public record ProblemHintUpsertRow(
    String problemSlug,
    String sourceSite,
    short ordinal,
    String contentMarkdown,
    String sourceSnapshot
) {
}
