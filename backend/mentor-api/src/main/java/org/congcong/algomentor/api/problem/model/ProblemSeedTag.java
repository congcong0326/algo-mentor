package org.congcong.algomentor.api.problem.model;

/**
 * 单题内已规范化的标签，ordinal 从 0 开始并保留 seed 顺序。
 */
public record ProblemSeedTag(
    String value,
    String labelEn,
    String labelZh,
    int ordinal
) {

  /** 题目内标签序号的起始值。 */
  public static final int FIRST_ORDINAL = 0;
}
