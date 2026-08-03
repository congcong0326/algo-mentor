package org.congcong.algomentor.mentor.application.learningplan.stream;

import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSnapshot;

/** Learning Plan Draft Definition 的受信业务输入。 */
public record LearningPlanDraftAgentInput(
    long userId,
    LearningPlanBrief brief,
    String idempotencyKey,
    LearningPlanPersonalizationSnapshot personalizationSnapshot
) {

  public LearningPlanDraftAgentInput(long userId, LearningPlanBrief brief, String idempotencyKey) {
    this(userId, brief, idempotencyKey, LearningPlanPersonalizationSnapshot.disabled(java.time.Instant.EPOCH));
  }

  public LearningPlanDraftAgentInput {
    if (userId < 1) {
      throw new IllegalArgumentException("Learning plan draft user id must be positive");
    }
    brief = Objects.requireNonNull(brief, "Learning plan draft brief must not be null");
    personalizationSnapshot = Objects.requireNonNull(
        personalizationSnapshot,
        "Learning plan draft personalization snapshot must not be null");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Learning plan draft idempotency key must not be blank");
    }
    idempotencyKey = idempotencyKey.trim();
  }
}
