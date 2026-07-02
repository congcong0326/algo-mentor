package org.congcong.algomentor.mentor.application.review;

import java.util.Optional;

/**
 * 复习卡所需的题面查询端口。
 */
public interface ReviewProblemCatalog {

  Optional<ReviewProblemSnapshot> findBySlug(String slug);
}
