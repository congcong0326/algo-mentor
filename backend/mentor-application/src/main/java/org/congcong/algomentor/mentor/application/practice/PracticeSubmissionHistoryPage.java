package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

/** keyset 分页读取的正式 Review 窄行结果；多出的探测行只由 repository 用于判断下一页。 */
public record PracticeSubmissionHistoryPage(
    List<PracticeSubmissionHistoryReview> submissions,
    boolean hasMore
) {

  public PracticeSubmissionHistoryPage {
    submissions = submissions == null ? List.of() : List.copyOf(submissions);
  }
}
