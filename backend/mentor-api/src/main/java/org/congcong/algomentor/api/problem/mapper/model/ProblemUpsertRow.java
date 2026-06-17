package org.congcong.algomentor.api.problem.mapper.model;

import java.sql.Array;

public record ProblemUpsertRow(
    String slug,
    Integer frontendId,
    String title,
    String titleCn,
    String difficulty,
    Array tags,
    String contentMarkdown,
    String leetcodeUrl,
    String sampleTestCase,
    String python3Template,
    String sourceCommit
) {
}
