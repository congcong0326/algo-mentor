package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSnapshot;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionDraft;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;

/** Learning Plan Extension Definition 的受信业务输入。 */
public record LearningPlanExtensionAgentInput(
    long userId,
    long planId,
    Long proposalGroupId,
    String instruction,
    LearningPlan plan,
    List<PracticeProgress> progress,
    LearningPlanExtensionDraft previousExtension,
    String idempotencyKey,
    LearningPlanPersonalizationSnapshot personalizationSnapshot
) {

  public LearningPlanExtensionAgentInput {
    if (userId < 1 || planId < 1) {
      throw new IllegalArgumentException("Learning plan extension identity must be positive");
    }
    if (proposalGroupId != null && proposalGroupId < 1) {
      throw new IllegalArgumentException("Learning plan extension proposal group id must be positive");
    }
    if (instruction == null || instruction.isBlank()) {
      throw new IllegalArgumentException("Learning plan extension instruction must not be blank");
    }
    plan = Objects.requireNonNull(plan, "Learning plan extension plan must not be null");
    progress = progress == null ? List.of() : List.copyOf(progress);
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Learning plan extension idempotency key must not be blank");
    }
    personalizationSnapshot = Objects.requireNonNull(
        personalizationSnapshot, "Learning plan extension personalization snapshot must not be null");
    instruction = instruction.trim();
    idempotencyKey = idempotencyKey.trim();
  }
}
