package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

/** 当前题关联题目的受信目录；关联仅用于生成迁移学习候选。 */
public interface PracticeRelatedProblemCatalog {

  List<String> findRelatedProblemSlugs(String problemSlug);

  static PracticeRelatedProblemCatalog empty() {
    return problemSlug -> List.of();
  }
}
