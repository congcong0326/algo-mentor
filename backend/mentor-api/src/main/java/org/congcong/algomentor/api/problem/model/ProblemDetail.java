package org.congcong.algomentor.api.problem.model;

import java.util.List;

public record ProblemDetail(
    String slug,
    Integer frontendId,
    String frontendDisplayId,
    String title,
    ProblemDifficulty difficulty,
    List<ProblemTag> tags,
    String contentMarkdown,
    String contentStatus,
    String leetcodeUrl,
    String sampleTestCase,
    String python3Template,
    String sourceCommit,
    String recommendationReason
) {

  public ProblemDetail {
    tags = tags == null ? List.of() : List.copyOf(tags);
  }
}
