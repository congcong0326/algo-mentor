package org.congcong.algomentor.mentor.application.learningplan.stream;

import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;

/** Learning Plan Draft Definition 的受信业务输入。 */
public record LearningPlanDraftAgentInput(
    long userId,
    LearningPlanDraftCommand command,
    String idempotencyKey
) {

  public LearningPlanDraftAgentInput {
    if (userId < 1) {
      throw new IllegalArgumentException("Learning plan draft user id must be positive");
    }
    command = Objects.requireNonNull(command, "Learning plan draft command must not be null");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Learning plan draft idempotency key must not be blank");
    }
    idempotencyKey = idempotencyKey.trim();
  }
}
