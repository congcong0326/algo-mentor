package org.congcong.algomentor.mentor.application.profile.review.history;

/** 仅供服务端差异计算使用的规范化提交版本；绝不能直接作为工具结果返回。 */
public record CodeReviewSubmissionVersion(
    long reviewId,
    String problemSlug,
    int versionNo,
    String normalizedCode
) {

  public CodeReviewSubmissionVersion {
    if (reviewId < 1 || problemSlug == null || problemSlug.isBlank() || versionNo < 1
        || normalizedCode == null || normalizedCode.isBlank()) {
      throw new IllegalArgumentException("Invalid code review submission version");
    }
    problemSlug = problemSlug.trim();
    normalizedCode = normalizedCode.strip();
  }
}
