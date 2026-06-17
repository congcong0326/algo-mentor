package org.congcong.algomentor.api.problem.model;

import java.util.List;

public record ProblemListItem(
    String slug,
    Integer frontendId,
    String title,
    String titleCn,
    ProblemDifficulty difficulty,
    List<String> tags
) {
}
