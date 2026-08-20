package org.congcong.algomentor.api.learningplan.model;

/**
 * 今日题包页的一次性加载响应，组合题包和最小计划执行上下文。
 */
public record TodayPackWorkspaceResponse(
    TodayPackResponse pack,
    TodayPackPlanContextResponse plan
) {
}
