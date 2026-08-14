package org.congcong.algomentor.api.problem.model;

import com.fasterxml.jackson.databind.JsonNode;

/** 一条有方向的来源题相似题关联 seed。 */
public record ProblemRelationSeedRecord(
    String sourceSlug,
    String targetSlug,
    String relationType,
    String source,
    String sourceSnapshot,
    JsonNode metadata
) {
}
