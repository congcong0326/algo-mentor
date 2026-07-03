package org.congcong.algomentor.api.problem.mapper.model;

public record ProblemCompanyImportRunRow(
    String sourceName,
    String sourceCommit,
    String manifestPath,
    int matchedSignalCount,
    int skippedSignalCount,
    int errorCount,
    String metadataJson
) {
}
