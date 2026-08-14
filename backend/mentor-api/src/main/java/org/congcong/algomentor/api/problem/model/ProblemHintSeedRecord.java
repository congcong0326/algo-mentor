package org.congcong.algomentor.api.problem.model;

/** 官方提示 seed；ordinal 保持来源页面顺序。 */
public record ProblemHintSeedRecord(
    String problemSlug,
    String sourceSite,
    short ordinal,
    String contentMarkdown,
    String sourceSnapshot
) {
}
