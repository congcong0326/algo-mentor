package org.congcong.algomentor.mentor.application.review.catalog;

import java.util.Optional;

public interface ReviewProblemCatalog {

  Optional<ReviewProblemSnapshot> findBySlug(String slug, String locale);

  /**
   * 未指定语言的后台和校验场景使用题目目录适配器的默认语言。
   */
  default Optional<ReviewProblemSnapshot> findBySlug(String slug) {
    return findBySlug(slug, null);
  }
}
