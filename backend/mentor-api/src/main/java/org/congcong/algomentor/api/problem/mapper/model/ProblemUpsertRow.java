package org.congcong.algomentor.api.problem.mapper.model;

import java.sql.Array;

public record ProblemUpsertRow(
    String slug,
    Integer frontendId,
    String titleEn,
    String titleZh,
    String difficulty,
    Array tagValues,
    Array tagLabelsEn,
    Array tagLabelsZh,
    String contentMarkdownEn,
    String contentMarkdownZh,
    String leetcodeUrl,
    String sampleTestCase,
    String python3Template,
    String sourceCommit
) {
}
