package org.congcong.algomentor.mentor.application.review.note;

import java.time.Instant;
import java.util.Optional;

public interface UserProblemNoteRepository {

  /** 默认只读入口；生产实现应避免读取 Markdown 正文。 */
  default Optional<UserProblemNoteSummary> findSummary(long userId, String problemSlug) {
    return find(userId, problemSlug).map(UserProblemNoteSummary::from);
  }

  Optional<UserProblemNote> find(long userId, String problemSlug);

  Optional<UserProblemNote> insert(
      long userId,
      String problemSlug,
      ProblemSolutionOutlineV1 outline,
      String noteMarkdown,
      Instant now
  );

  Optional<UserProblemNote> update(
      long userId,
      String problemSlug,
      ProblemSolutionOutlineV1 outline,
      long expectedRevision,
      Instant now
  );

  /** 原子替换教练总结；结构化提纲及其 revision 必须保持不变。 */
  default Optional<UserProblemNote> replaceCoachSummary(
      long userId,
      String problemSlug,
      ProblemSolutionOutlineV1 initialOutline,
      String summaryMarkdown,
      long expectedCoachSummaryRevision,
      Instant now
  ) {
    throw new UnsupportedOperationException("Coach summary replacement is not implemented");
  }

  boolean delete(long userId, String problemSlug);
}
