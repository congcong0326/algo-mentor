package org.congcong.algomentor.api.review.model;

import java.util.List;

/** 复习中心复习卡分页响应。 */
public record ReviewCardOverviewPageResponse(
    List<ReviewCardOverviewResponse> items,
    long total,
    long activeCount,
    long mistakeCount,
    int page,
    int pageSize
) {
  public ReviewCardOverviewPageResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
