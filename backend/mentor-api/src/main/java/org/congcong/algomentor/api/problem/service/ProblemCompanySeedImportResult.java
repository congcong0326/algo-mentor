package org.congcong.algomentor.api.problem.service;

public record ProblemCompanySeedImportResult(
    int readSignalCount,
    int matchedSignalCount,
    int skippedSignalCount
) {
}
