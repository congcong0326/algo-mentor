package org.congcong.algomentor.mentor.application.profile.review.history;

/** 受限 unified diff 的结果，截断仅发生在完整 hunk 之间。 */
public record SubmissionVersionDiff(String unifiedDiff, boolean truncated) {

  public SubmissionVersionDiff {
    unifiedDiff = unifiedDiff == null ? "" : unifiedDiff;
    if (unifiedDiff.length() > SubmissionVersionDiffService.MAX_DIFF_CHARS) {
      throw new IllegalArgumentException("Submission diff exceeds maximum length");
    }
  }
}
