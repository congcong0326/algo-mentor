package org.congcong.algomentor.mentor.application.review;

import java.util.List;

public record ReviewCard(
    CardVariant cardVariant,
    ProblemRef problemRef,
    String contextSummary,
    List<ReviewCardPrompt> prompts,
    ReviewCardScaffold scaffold,
    String revealPolicy,
    String expectedEffort
) {
  public ReviewCard {
    prompts = prompts == null ? List.of() : List.copyOf(prompts);
    if (revealPolicy == null || revealPolicy.isBlank()) {
      revealPolicy = MistakeReviewConstants.REVEAL_HIDE_PREVIOUS;
    }
  }
}
