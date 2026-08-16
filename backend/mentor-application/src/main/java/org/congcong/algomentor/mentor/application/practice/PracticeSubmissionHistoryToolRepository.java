package org.congcong.algomentor.mentor.application.practice;

import java.time.Instant;
import java.util.Optional;

/**
 * Phase 2 历史提交 Tool 的专用读取端口。
 *
 * <p>调用方必须先从 run-local scope 解析用户和题目/Review 键；实现不得提供按裸 Review ID 的读取路径。</p>
 */
public interface PracticeSubmissionHistoryToolRepository {

  Optional<PracticeSubmissionHistoryOverview> findOverview(long userId, String problemSlug);

  PracticeSubmissionHistoryPage findSubmissions(
      long userId,
      String problemSlug,
      Instant afterCreatedAt,
      Long afterReviewId,
      int limit
  );

  Optional<PracticeSubmissionHistoryDetail> findSubmissionDetail(long userId, String problemSlug, long reviewId);
}
