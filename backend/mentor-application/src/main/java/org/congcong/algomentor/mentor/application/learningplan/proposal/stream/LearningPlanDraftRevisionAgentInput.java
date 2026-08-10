package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSnapshot;

/** Learning Plan Draft Revision Definition 的受信业务输入。 */
public record LearningPlanDraftRevisionAgentInput(
    long userId,
    long draftId,
    long revisionId,
    String instruction,
    LearningPlanBrief baseBrief,
    LearningPlanDraftPlan basePlan,
    String idempotencyKey,
    LearningPlanPersonalizationSnapshot personalizationSnapshot
) {

  public LearningPlanDraftRevisionAgentInput {
    if (userId < 1 || draftId < 1 || revisionId < 1) {
      throw new IllegalArgumentException("Learning plan draft revision identity must be positive");
    }
    if (instruction == null || instruction.isBlank()) {
      throw new IllegalArgumentException("Learning plan draft revision instruction must not be blank");
    }
    baseBrief = Objects.requireNonNull(baseBrief, "Learning plan draft revision base brief must not be null");
    basePlan = Objects.requireNonNull(basePlan, "Learning plan draft revision base plan must not be null");
    personalizationSnapshot = Objects.requireNonNull(
        personalizationSnapshot, "Learning plan draft revision personalization snapshot must not be null");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Learning plan draft revision idempotency key must not be blank");
    }
    instruction = instruction.trim();
    idempotencyKey = idempotencyKey.trim();
  }
}
