package org.congcong.algomentor.api.problem.model;

import java.util.Objects;

/** 规范化题目标签的稳定目录 ID 与双语显示值。 */
public record TrustedProblemTag(long tagId, String value, String labelEn, String labelZh) {

  public TrustedProblemTag {
    if (tagId < 1) {
      throw new IllegalArgumentException("tagId must be positive");
    }
    value = Objects.requireNonNull(value, "value must not be null");
    labelEn = Objects.requireNonNull(labelEn, "labelEn must not be null");
    labelZh = Objects.requireNonNull(labelZh, "labelZh must not be null");
  }
}
