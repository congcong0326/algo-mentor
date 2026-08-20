package org.congcong.algomentor.mentor.application.review.card;

import java.util.List;

/** 复习中心复习卡分页结果。 */
public record ReviewCardOverviewPage(
    List<ReviewCardOverview> items,
    long total,
    long activeCount,
    long mistakeCount,
    int page,
    int pageSize
) {
  public ReviewCardOverviewPage {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
