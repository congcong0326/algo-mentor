package org.congcong.algomentor.api.problem.model;

import java.util.List;

/**
 * 一次整批标签规范化的不可变输出。
 */
public record ProblemTagNormalizationResult(
    List<ProblemTagDefinition> catalog,
    List<NormalizedProblemSeed> problems
) {

  public ProblemTagNormalizationResult {
    catalog = List.copyOf(catalog);
    problems = List.copyOf(problems);
  }
}
