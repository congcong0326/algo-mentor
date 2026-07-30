package org.congcong.algomentor.mentor.application.profile.review;

import java.util.List;

/** Code Review 画像后台 Agent 的受信 review 窗口与画像候选输入。 */
public record CodeReviewProfileUpdateAgentInput(
    long userId,
    List<CodeReviewProfileFact> facts,
    List<CodeReviewProfilePromptBuilder.Candidate> candidates,
    String idempotencyKey,
    Long retryOfRunId
) {

  public CodeReviewProfileUpdateAgentInput {
    if (userId < 1) {
      throw new IllegalArgumentException("Code review profile Agent user id must be positive");
    }
    if (facts == null || facts.isEmpty() || facts.stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException("Code review profile Agent facts must not be empty");
    }
    if (candidates == null || candidates.isEmpty() || candidates.stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException("Code review profile Agent candidates must not be empty");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Code review profile Agent idempotency key must not be blank");
    }
    if (retryOfRunId != null && retryOfRunId < 1) {
      throw new IllegalArgumentException("Code review profile Agent retry source run id must be positive");
    }
    facts = List.copyOf(facts);
    candidates = List.copyOf(candidates);
    idempotencyKey = idempotencyKey.trim();
  }
}
