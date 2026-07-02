package org.congcong.algomentor.api.review.model;

public record ReviewProblemStatementResponse(
    String slug,
    String titleCn,
    String difficulty,
    String contentMarkdown
) {
}
