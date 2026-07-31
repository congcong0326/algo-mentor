package org.congcong.algomentor.mentor.application.profile.review.history;

import java.util.List;
import java.util.Optional;

/**
 * Code Review 更新链路的受信读取端口。
 *
 * <p>所有方法都以用户为第一范围条件，调用方不得以裸 Review ID 查询。</p>
 */
public interface CodeReviewHistoryRepository {

  List<CodeReviewHistory> findLatestForProblem(long userId, String problemSlug, int limit);

  Optional<CodeReviewEvidenceDetail> findEvidenceDetail(long userId, long reviewId);

  /** 仅供 {@link SubmissionVersionDiffService} 使用的有界规范化代码读取。 */
  List<CodeReviewSubmissionVersion> findNormalizedSubmissionVersions(long userId, List<Long> reviewIds);

  List<CodeReviewVerification> verifyReviews(long userId, List<Long> reviewIds);
}
