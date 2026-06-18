package org.congcong.algomentor.api.problem.model;

import java.util.List;

public record ProblemFilters(
    long problemCount,
    List<ProblemFilterOption> difficulties,
    List<ProblemFilterOption> tags,
    List<ProblemCategoryFilterOption> categories
) {

  public ProblemFilters {
    difficulties = difficulties == null ? List.of() : List.copyOf(difficulties);
    tags = tags == null ? List.of() : List.copyOf(tags);
    categories = categories == null ? List.of() : List.copyOf(categories);
  }
}
