package org.congcong.algomentor.mentor.application.review.catalog;

public record ReviewProblemSnapshot(
    String slug,
    String titleCn,
    String difficulty,
    String statementSummary,
    String fullStatementMarkdown
) {
}
