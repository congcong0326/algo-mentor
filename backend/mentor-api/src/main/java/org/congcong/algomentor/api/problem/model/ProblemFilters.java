package org.congcong.algomentor.api.problem.model;

import java.util.List;

public record ProblemFilters(
    long problemCount,
    List<ProblemFilterOption> difficulties,
    List<ProblemFilterOption> tags,
    List<ProblemCategoryFilterOption> categories,
    List<ProblemFilterOption> companies,
    List<ProblemFilterOption> roles,
    List<ProblemFilterOption> recencyBuckets
) {

  public ProblemFilters {
    difficulties = difficulties == null ? List.of() : List.copyOf(difficulties);
    tags = tags == null ? List.of() : List.copyOf(tags);
    categories = categories == null ? List.of() : List.copyOf(categories);
    companies = companies == null ? List.of() : List.copyOf(companies);
    roles = roles == null ? List.of() : List.copyOf(roles);
    recencyBuckets = recencyBuckets == null ? List.of() : List.copyOf(recencyBuckets);
  }
}
