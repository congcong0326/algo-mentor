package org.congcong.algomentor.api.activity.mapper.model;

import java.time.LocalDate;

/** 按用户时区聚合后的单日正式代码 Review 数量。 */
public record ActivityContributionRow(LocalDate activityDate, int activityCount) {
}
