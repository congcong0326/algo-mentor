package org.congcong.algomentor.mentor.application.practice;

import java.util.Objects;

/** Practice Code Review child Definition 的受信业务输入。 */
public record PracticeCodeReviewAgentInput(
    PracticeTurnContext context,
    String idempotencyKey
) {

  public PracticeCodeReviewAgentInput {
    context = Objects.requireNonNull(context, "Practice code review context must not be null");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Practice code review idempotency key must not be blank");
    }
    idempotencyKey = idempotencyKey.trim();
  }
}
