package org.congcong.algomentor.api.problem.model;

import java.util.List;

public record ProblemDetail(
    String slug,
    Integer frontendId,
    String title,
    ProblemDifficulty difficulty,
    List<ProblemTag> tags,
    String contentMarkdown,
    String leetcodeUrl,
    String sampleTestCase,
    String python3Template,
    String sourceCommit
) {

  public ProblemDetail {
    tags = tags == null ? List.of() : List.copyOf(tags);
  }
}
