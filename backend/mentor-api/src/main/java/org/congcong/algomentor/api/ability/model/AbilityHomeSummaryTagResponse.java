package org.congcong.algomentor.api.ability.model;

/**
 * 首页学习诊断中展示的标签信息。
 */
public record AbilityHomeSummaryTagResponse(
    String label,
    long reviewedProblemCount
) {
}
