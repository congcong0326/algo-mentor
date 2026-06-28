package org.congcong.algomentor.api.ability.model;

public record AbilityProfileScopeResponse(
    int minProblemCount,
    int scorePrecision,
    boolean latestReviewOnly,
    int conservativeWeight
) {
}
