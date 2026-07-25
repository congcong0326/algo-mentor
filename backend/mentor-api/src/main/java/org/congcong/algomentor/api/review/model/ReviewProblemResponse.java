package org.congcong.algomentor.api.review.model;

public record ReviewProblemResponse(
    String slug,
    String titleCn,
    String difficulty,
    String contentMarkdown
) {
}
