package org.congcong.algomentor.api.problem.mapper.model;

public record ProblemRow(
    Long id,
    String slug,
    Integer frontendId,
    String title,
    String titleCn,
    String difficulty,
    String tagsText,
    String contentMarkdown,
    String leetcodeUrl,
    String sampleTestCase,
    String python3Template,
    String sourceCommit
) {
}
