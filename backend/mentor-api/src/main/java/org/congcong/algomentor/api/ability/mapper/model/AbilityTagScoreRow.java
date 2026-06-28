package org.congcong.algomentor.api.ability.mapper.model;

import java.math.BigDecimal;

public record AbilityTagScoreRow(
    String tag,
    String label,
    long problemCount,
    long reviewedProblemCount,
    BigDecimal rawAverageScore
) {
}
