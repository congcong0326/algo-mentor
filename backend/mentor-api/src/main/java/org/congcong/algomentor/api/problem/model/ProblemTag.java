package org.congcong.algomentor.api.problem.model;

/**
 * 题目标签：value 用于稳定过滤，label 用于当前语言展示。
 */
public record ProblemTag(
    String value,
    String label
) {
}
