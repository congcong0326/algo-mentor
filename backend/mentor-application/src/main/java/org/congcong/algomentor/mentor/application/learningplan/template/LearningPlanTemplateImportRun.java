package org.congcong.algomentor.mentor.application.learningplan.template;

import java.util.Map;

public record LearningPlanTemplateImportRun(
    String sourceName,
    String sourceCommit,
    String seedPath,
    String manifestPath,
    String metadataPath,
    String checksum,
    int templateCount,
    int problemRefCount,
    int matchedProblemCount,
    int missingProblemCount,
    int errorCount,
    Map<String, Object> metadata
) {

  public LearningPlanTemplateImportRun {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
