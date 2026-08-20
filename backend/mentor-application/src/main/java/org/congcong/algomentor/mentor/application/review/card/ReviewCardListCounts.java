package org.congcong.algomentor.mentor.application.review.card;

/** 复习中心当前筛选条件下的卡片汇总。 */
public record ReviewCardListCounts(long total, long activeCount, long mistakeCount) {
}
