package org.congcong.algomentor.api.review.model;

import java.util.List;

/** 复习中心列表专用响应，避免影响归档和详情接口契约。 */
public record ReviewCardOverviewResponse(
    ReviewCardResponse card,
    List<PracticeCodeReviewIndexEntryResponse> recentCodeReviews
) {
  public ReviewCardOverviewResponse {
    recentCodeReviews = recentCodeReviews == null ? List.of() : List.copyOf(recentCodeReviews);
  }
}
