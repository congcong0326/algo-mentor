package org.congcong.algomentor.api.ability.model;

import java.math.BigDecimal;

public record AbilityTagScoreResponse(
    String tag,
    String label,
    long problemCount,
    long reviewedProblemCount,
    BigDecimal rawAverageScore,
    BigDecimal abilityScore
) {
}
