package org.congcong.algomentor.api.learningplan.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;

public record LearningPlanTemplateImportRunRow(
    String sourceName,
    String sourceCommit,
    String seedPath,
    String manifestPath,
    String metadataPath,
    String checksum,
    Integer templateCount,
    Integer problemRefCount,
    Integer matchedProblemCount,
    Integer missingProblemCount,
    Integer errorCount,
    JsonNode metadataJson
) {
}
