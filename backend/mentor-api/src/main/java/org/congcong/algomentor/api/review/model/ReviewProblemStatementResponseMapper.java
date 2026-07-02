package org.congcong.algomentor.api.review.model;

import org.congcong.algomentor.mentor.application.review.ReviewProblemSnapshot;

public final class ReviewProblemStatementResponseMapper {

  private ReviewProblemStatementResponseMapper() {
  }

  public static ReviewProblemStatementResponse toResponse(ReviewProblemSnapshot snapshot) {
    return new ReviewProblemStatementResponse(
        snapshot.slug(),
        snapshot.titleCn(),
        snapshot.difficulty(),
        snapshot.fullStatementMarkdown());
  }
}
