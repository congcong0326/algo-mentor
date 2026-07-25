package org.congcong.algomentor.mentor.application.review.catalog;

import java.util.Optional;

public interface ReviewProblemCatalog {

  Optional<ReviewProblemSnapshot> findBySlug(String slug);
}
