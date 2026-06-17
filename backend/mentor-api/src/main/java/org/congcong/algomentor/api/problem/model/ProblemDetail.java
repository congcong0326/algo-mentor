package org.congcong.algomentor.api.problem.model;

import java.util.List;

public record ProblemDetail(
    String slug,
    Integer frontendId,
    String title,
    String titleCn,
    ProblemDifficulty difficulty,
    List<String> tags,
    String contentMarkdown,
    String leetcodeUrl,
    String sampleTestCase,
    String python3Template,
    String sourceCommit
) {
}
