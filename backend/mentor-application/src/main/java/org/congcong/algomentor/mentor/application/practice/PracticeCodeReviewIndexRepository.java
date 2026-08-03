package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

/** 按题目批量读取复习中心代码 Review 时间线的窄查询端口。 */
public interface PracticeCodeReviewIndexRepository {

  List<PracticeCodeReviewIndexEntry> findRecentByProblemSlugs(
      long userId,
      List<String> problemSlugs,
      int perProblemLimit
  );
}
