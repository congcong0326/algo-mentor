package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

/** Phase 1 Prompt 历史提交索引的唯一 Review 读取边界。 */
public interface PracticeSubmissionHistoryRepository {

  List<PracticeSubmissionHistoryProblem> findRecentDistinctProblems(long userId, int limit);

  List<PracticeSubmissionHistoryProblem> findRecentDistinctProblemsForSlugs(
      long userId,
      List<String> problemSlugs,
      int limit
  );

  static PracticeSubmissionHistoryRepository empty() {
    return new PracticeSubmissionHistoryRepository() {
      @Override
      public List<PracticeSubmissionHistoryProblem> findRecentDistinctProblems(long userId, int limit) {
        return List.of();
      }

      @Override
      public List<PracticeSubmissionHistoryProblem> findRecentDistinctProblemsForSlugs(
          long userId, List<String> problemSlugs, int limit) {
        return List.of();
      }
    };
  }
}
