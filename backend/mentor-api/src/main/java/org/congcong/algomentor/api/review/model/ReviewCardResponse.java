package org.congcong.algomentor.api.review.model;

import java.util.List;

public record ReviewCardResponse(
    String cardVariant,
    ProblemRefResponse problemRef,
    String contextSummary,
    List<ReviewCardPromptResponse> prompts,
    ReviewCardScaffoldResponse scaffold,
    String revealPolicy,
    String expectedEffort
) {
}
