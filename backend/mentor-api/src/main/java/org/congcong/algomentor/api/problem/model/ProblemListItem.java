package org.congcong.algomentor.api.problem.model;

import java.math.BigDecimal;
import java.util.List;

public record ProblemListItem(
    String slug,
    Integer frontendId,
    String frontendDisplayId,
    String title,
    ProblemDifficulty difficulty,
    List<ProblemTag> tags,
    String contentStatus,
    BigDecimal companyFrequencyScore,
    long companySignalCount
) {

  public ProblemListItem {
    tags = tags == null ? List.of() : List.copyOf(tags);
  }
}
