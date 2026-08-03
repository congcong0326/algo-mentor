package org.congcong.algomentor.mentor.application.review.card;

import java.util.List;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewIndexEntry;

/** 复习卡及其最近代码 Review 索引的列表读模型。 */
public record ReviewCardOverview(
    ProblemReviewCard card,
    List<PracticeCodeReviewIndexEntry> recentCodeReviews
) {
  public ReviewCardOverview {
    recentCodeReviews = recentCodeReviews == null ? List.of() : List.copyOf(recentCodeReviews);
  }
}
