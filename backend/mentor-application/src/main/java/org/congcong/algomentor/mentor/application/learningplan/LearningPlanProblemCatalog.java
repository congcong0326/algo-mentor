package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;
import java.util.Optional;

public interface LearningPlanProblemCatalog {

  List<LearningPlanProblemCandidate> searchProblems(LearningPlanProblemSearch search);

  Optional<LearningPlanProblemCandidate> findBySlug(String slug);

  /**
   * 按请求语言读取题目候选项；默认实现保留既有题目目录适配器的兼容性。
   */
  default Optional<LearningPlanProblemCandidate> findBySlug(String slug, String locale) {
    return findBySlug(slug);
  }
}
