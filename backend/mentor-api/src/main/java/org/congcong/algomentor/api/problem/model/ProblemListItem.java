package org.congcong.algomentor.api.problem.model;

import java.util.List;

public record ProblemListItem(
    String slug,
    Integer frontendId,
    String title,
    ProblemDifficulty difficulty,
    List<ProblemTag> tags
) {

  public ProblemListItem {
    tags = tags == null ? List.of() : List.copyOf(tags);
  }
}
