package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

/** Code Review 生成链路的同题跨计划历史窄查询端口。 */
public interface PracticeCodeReviewHistoryRepository {

  List<PracticeCodeReviewHistoricalFact> findRecentForProblem(long userId, String problemSlug, int limit);

  static PracticeCodeReviewHistoryRepository empty() {
    return (userId, problemSlug, limit) -> List.of();
  }
}
