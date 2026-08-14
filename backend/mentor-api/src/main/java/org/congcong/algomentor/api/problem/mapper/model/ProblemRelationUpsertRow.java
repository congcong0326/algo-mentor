package org.congcong.algomentor.api.problem.mapper.model;

public record ProblemRelationUpsertRow(
    String sourceSlug,
    String targetSlug,
    String relationType,
    String source,
    String sourceSnapshot,
    String metadataJson
) {
}
