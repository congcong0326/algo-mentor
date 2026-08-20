package org.congcong.algomentor.api.ability.model;

import java.math.BigDecimal;

/**
 * 首页学习诊断所需的最小能力数据集。
 */
public record AbilityHomeSummaryResponse(
    BigDecimal averageAbilityScore,
    AbilityHomeSummaryTagResponse currentStrength,
    AbilityHomeSummaryTagResponse nextBreakthrough
) {
}
