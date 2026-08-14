package org.congcong.algomentor.api.problem.model;

/** 一次受审元数据 seed 导入的可观测结果。 */
public record ProblemLearningMetadataImportResult(
    int readCount,
    int matchedSourceProblemCount,
    int skippedSourceProblemCount,
    String sourceSnapshot
) {
}
