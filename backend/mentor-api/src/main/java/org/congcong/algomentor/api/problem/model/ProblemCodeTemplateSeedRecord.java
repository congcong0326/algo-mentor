package org.congcong.algomentor.api.problem.model;

/** LeetCode 某题某语言的 starter code seed。 */
public record ProblemCodeTemplateSeedRecord(
    String problemSlug,
    String languageSlug,
    String languageLabel,
    String code,
    String sourceSite,
    String sourceSnapshot
) {
}
