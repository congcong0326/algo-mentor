package org.congcong.algomentor.mentor.application.practice;

/** 当前题目规范化关联表提供的受信 Review 标签候选。 */
public record TrustedProblemTag(long tagId, String value, String labelEn, String labelZh) {
  public TrustedProblemTag {
    if (tagId < 1 || value == null || value.isBlank() || labelEn == null || labelEn.isBlank()
        || labelZh == null || labelZh.isBlank()) {
      throw new IllegalArgumentException("Invalid trusted problem tag");
    }
    value = value.trim();
    labelEn = labelEn.trim();
    labelZh = labelZh.trim();
  }
}
