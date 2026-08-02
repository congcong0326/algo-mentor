package org.congcong.algomentor.api.review.model;

public record ReviewProblemResponse(
    String slug,
    String title,
    String difficulty,
    String contentMarkdown
) {
}
