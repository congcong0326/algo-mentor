package org.congcong.algomentor.api.problem.mapper.model;

public record ProblemMetadataImportRunRow(
    String manifestPath,
    String manifestSha256,
    String sourceSnapshot,
    int readCount,
    int matchedCount,
    int skippedCount,
    int failedCount,
    String auditReportJson
) {
}
