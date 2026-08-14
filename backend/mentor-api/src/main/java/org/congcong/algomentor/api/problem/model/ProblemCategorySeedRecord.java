package org.congcong.algomentor.api.problem.model;

/** 受控来源分类目录 seed。 */
public record ProblemCategorySeedRecord(
    String slug,
    String nameEn,
    String nameZh,
    String source,
    String sourceSnapshot
) {
}
