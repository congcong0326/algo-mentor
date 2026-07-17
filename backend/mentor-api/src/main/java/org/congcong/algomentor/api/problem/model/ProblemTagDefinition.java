package org.congcong.algomentor.api.problem.model;

/**
 * 从完整题目 seed 中稳定决胜得到的标签目录项。
 */
public record ProblemTagDefinition(
    String value,
    String labelEn,
    String labelZh
) {
}
