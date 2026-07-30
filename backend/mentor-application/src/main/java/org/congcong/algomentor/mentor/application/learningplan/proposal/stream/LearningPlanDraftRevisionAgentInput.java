package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;

/** Learning Plan Draft Revision Definition 的受信业务输入。 */
public record LearningPlanDraftRevisionAgentInput(
    long userId,
    long draftId,
    String instruction,
    LearningPlanDraftCommand command,
    LearningPlanDraftPlan currentPlan,
    String idempotencyKey
) {

  public LearningPlanDraftRevisionAgentInput {
    if (userId < 1 || draftId < 1) {
      throw new IllegalArgumentException("Learning plan draft revision identity must be positive");
    }
    if (instruction == null || instruction.isBlank()) {
      throw new IllegalArgumentException("Learning plan draft revision instruction must not be blank");
    }
    command = Objects.requireNonNull(command, "Learning plan draft revision command must not be null");
    currentPlan = Objects.requireNonNull(currentPlan, "Learning plan draft revision current plan must not be null");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Learning plan draft revision idempotency key must not be blank");
    }
    instruction = instruction.trim();
    idempotencyKey = idempotencyKey.trim();
  }
}
