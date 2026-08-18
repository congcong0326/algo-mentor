package org.congcong.algomentor.mentor.application.profile.review;

import java.util.List;

/** 读取 Code Review 画像轻量事实的端口，调用方不接触代码和完整 Review 文本。 */
public interface LearnerMemoryCodeReviewFactRepository {

  /** 当前用户全部正式 Review 的轻量事实；仅供服务端快照聚合，绝不直接拼接原始内容。 */
  List<LearnerMemoryCodeReviewFact> findAllForUser(long userId);

  List<LearnerMemoryCodeReviewFact> findByReviewIds(long userId, List<Long> reviewIds);

  List<LearnerMemoryCodeReviewFact> findLatestForProblemSlugs(long userId, List<String> problemSlugs);

  List<LearnerMemoryCodeReviewFact> findRecentDistinctProblems(long userId, List<String> excludedProblemSlugs, int limit);
}
