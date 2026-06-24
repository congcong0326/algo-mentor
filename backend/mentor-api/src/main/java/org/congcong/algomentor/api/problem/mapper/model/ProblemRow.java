package org.congcong.algomentor.api.problem.mapper.model;

public record ProblemRow(
    Long id,
    String slug,
    Integer frontendId,
    String titleEn,
    String titleZh,
    String difficulty,
    String tagValuesText,
    String tagLabelsEnText,
    String tagLabelsZhText,
    String contentMarkdownEn,
    String contentMarkdownZh,
    String leetcodeUrl,
    String sampleTestCase,
    String python3Template,
    String sourceCommit
) {
}
