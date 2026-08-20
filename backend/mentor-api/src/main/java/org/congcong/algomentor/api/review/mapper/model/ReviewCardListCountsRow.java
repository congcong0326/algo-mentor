package org.congcong.algomentor.api.review.mapper.model;

/** 复习卡列表分页与概览统计的数据库投影。 */
public record ReviewCardListCountsRow(long total, long activeCount, long mistakeCount) {
}
