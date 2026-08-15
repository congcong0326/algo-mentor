package org.congcong.algomentor.mentor.application.practice;

import java.util.Objects;
import java.util.List;

/** Practice Code Review child Definition 的受信业务输入。 */
public record PracticeCodeReviewAgentInput(
    PracticeTurnContext context,
    String idempotencyKey,
    List<PracticeCodeReviewHistoricalFact> historicalReviews,
    boolean historyLookupFailed
) {

  public PracticeCodeReviewAgentInput(PracticeTurnContext context, String idempotencyKey) {
    this(context, idempotencyKey, List.of(), false);
  }

  public PracticeCodeReviewAgentInput(
      PracticeTurnContext context,
      String idempotencyKey,
      List<PracticeCodeReviewHistoricalFact> historicalReviews
  ) {
    this(context, idempotencyKey, historicalReviews, false);
  }

  public PracticeCodeReviewAgentInput {
    context = Objects.requireNonNull(context, "Practice code review context must not be null");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Practice code review idempotency key must not be blank");
    }
    idempotencyKey = idempotencyKey.trim();
    historicalReviews = historicalReviews == null ? List.of() : List.copyOf(historicalReviews);
    if (historicalReviews.size() > 4) {
      throw new IllegalArgumentException("Practice code review history may contain at most four reviews");
    }
  }
}
