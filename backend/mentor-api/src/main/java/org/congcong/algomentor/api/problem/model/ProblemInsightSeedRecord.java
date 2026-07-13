package org.congcong.algomentor.api.problem.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProblemInsightSeedRecord(
    String slug,
    @JsonProperty("reasonEN") String reasonEn,
    @JsonProperty("reasonZH") String reasonZh
) {
}
