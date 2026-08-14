package org.congcong.algomentor.api.problem.model;

/** 题目到来源分类的关联 seed。 */
public record ProblemCategoryItemSeedRecord(
    String problemSlug,
    String categorySlug,
    String source,
    String sourceSnapshot
) {
}
