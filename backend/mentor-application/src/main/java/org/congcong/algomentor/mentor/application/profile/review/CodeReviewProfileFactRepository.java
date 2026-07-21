package org.congcong.algomentor.mentor.application.profile.review;

import java.util.List;

/** 读取 Code Review 画像轻量事实的端口，调用方不接触代码和完整 Review 文本。 */
public interface CodeReviewProfileFactRepository {

  List<CodeReviewProfileFact> findByReviewIds(long userId, List<Long> reviewIds);

  List<CodeReviewProfileFact> findLatestForProblemSlugs(long userId, List<String> problemSlugs);

  List<CodeReviewProfileFact> findRecentDistinctProblems(long userId, List<String> excludedProblemSlugs, int limit);
}
