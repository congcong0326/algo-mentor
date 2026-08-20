package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.time.Instant;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;

public record LearningPlanDraftRevision(
    Long id,
    long proposalGroupId,
    long draftId,
    long userId,
    int revisionNo,
    LearningPlanProposalRevisionStatus status,
    String instruction,
    LearningPlanBrief baseBrief,
    LearningPlanDraftPlan basePlan,
    LearningPlanBrief proposedBrief,
    LearningPlanDraftPlan proposedPlan,
    String errorCode,
    String errorMessage,
    String generationRequestKey,
    String generationRequestFingerprint,
    String generationRunId,
    Instant generationStartedAt,
    Instant generationCompletedAt,
    Instant createdAt,
    Instant updatedAt
) {

  /** 阶段二前的调用点不携带生成控制面元数据。 */
  public LearningPlanDraftRevision(
      Long id,
      long proposalGroupId,
      long draftId,
      long userId,
      int revisionNo,
      LearningPlanProposalRevisionStatus status,
      String instruction,
      LearningPlanBrief baseBrief,
      LearningPlanDraftPlan basePlan,
      LearningPlanBrief proposedBrief,
      LearningPlanDraftPlan proposedPlan,
      String errorCode,
      String errorMessage,
      Instant createdAt,
      Instant updatedAt
  ) {
    this(id, proposalGroupId, draftId, userId, revisionNo, status, instruction, baseBrief, basePlan,
        proposedBrief, proposedPlan, errorCode, errorMessage, null, null, null, null, null, createdAt, updatedAt);
  }

  public LearningPlanDraftRevision {
    if (id != null && id < 1) {
      throw new IllegalArgumentException("Learning plan draft revision id must be positive");
    }
    if (proposalGroupId < 1) {
      throw new IllegalArgumentException("Learning plan proposal group id must be positive");
    }
    if (draftId < 1) {
      throw new IllegalArgumentException("Learning plan draft id must be positive");
    }
    if (userId < 1) {
      throw new IllegalArgumentException("Learning plan draft revision user id must be positive");
    }
    if (revisionNo < 1) {
      throw new IllegalArgumentException("Learning plan draft revision number must be positive");
    }
    if (status == null) {
      throw new IllegalArgumentException("Learning plan draft revision status must not be null");
    }
    if (instruction == null || instruction.isBlank()) {
      throw new IllegalArgumentException("Learning plan draft revision instruction must not be blank");
    }
    if (baseBrief == null || basePlan == null) {
      throw new IllegalArgumentException("Learning plan draft revision base snapshot must not be null");
    }
    if (status == LearningPlanProposalRevisionStatus.READY && (proposedBrief == null || proposedPlan == null)) {
      throw new IllegalArgumentException("Learning plan draft revision proposed snapshot must not be null when ready");
    }
    if (status == LearningPlanProposalRevisionStatus.FAILED
        && (errorCode == null || errorCode.isBlank() || errorMessage == null || errorMessage.isBlank())) {
      throw new IllegalArgumentException("Learning plan draft revision failure detail must not be blank when failed");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("Learning plan draft revision created time must not be null");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("Learning plan draft revision updated time must not be null");
    }
    instruction = instruction.trim();
    generationRequestKey = generationRequestKey == null ? null : generationRequestKey.trim();
  }

  /** 兼容旧调用点；新修订必须显式传入冻结的 baseBrief。 */
  public LearningPlanDraftRevision(
      Long id,
      long proposalGroupId,
      long draftId,
      long userId,
      int revisionNo,
      LearningPlanProposalRevisionStatus status,
      String instruction,
      LearningPlanDraftPlan basePlan,
      LearningPlanDraftPlan proposedPlan,
      String errorCode,
      String errorMessage,
      Instant createdAt,
      Instant updatedAt
  ) {
    this(
        id,
        proposalGroupId,
        draftId,
        userId,
        revisionNo,
        status,
        instruction,
        briefFromPlan(basePlan),
        basePlan,
        proposedPlan == null ? null : briefFromPlan(proposedPlan),
        proposedPlan,
        errorCode,
        errorMessage,
        null,
        null,
        null,
        null,
        null,
        createdAt,
        updatedAt);
  }

  public LearningPlanDraftRevision withId(Long nextId) {
    return new LearningPlanDraftRevision(
        nextId,
        proposalGroupId,
        draftId,
        userId,
        revisionNo,
        status,
        instruction,
        baseBrief,
        basePlan,
        proposedBrief,
        proposedPlan,
        errorCode,
        errorMessage,
        generationRequestKey,
        generationRequestFingerprint,
        generationRunId,
        generationStartedAt,
        generationCompletedAt,
        createdAt,
        updatedAt);
  }

  public LearningPlanDraftRevision withStatus(LearningPlanProposalRevisionStatus nextStatus, Instant updatedAt) {
    return new LearningPlanDraftRevision(
        id,
        proposalGroupId,
        draftId,
        userId,
        revisionNo,
        nextStatus,
        instruction,
        baseBrief,
        basePlan,
        proposedBrief,
        proposedPlan,
        errorCode,
        errorMessage,
        generationRequestKey,
        generationRequestFingerprint,
        generationRunId,
        generationStartedAt,
        generationCompletedAt,
        createdAt,
        updatedAt);
  }

  public LearningPlanDraftRevision withCompiled(
      LearningPlanBrief nextProposedBrief,
      LearningPlanDraftPlan nextProposedPlan,
      Instant updatedAt
  ) {
    return new LearningPlanDraftRevision(
        id,
        proposalGroupId,
        draftId,
        userId,
        revisionNo,
        LearningPlanProposalRevisionStatus.GENERATING,
        instruction,
        baseBrief,
        basePlan,
        nextProposedBrief,
        nextProposedPlan,
        null,
        null,
        generationRequestKey,
        generationRequestFingerprint,
        generationRunId,
        generationStartedAt,
        generationCompletedAt,
        createdAt,
        updatedAt);
  }

  public LearningPlanDraftRevision withReady(Instant updatedAt) {
    return new LearningPlanDraftRevision(
        id,
        proposalGroupId,
        draftId,
        userId,
        revisionNo,
        LearningPlanProposalRevisionStatus.READY,
        instruction,
        baseBrief,
        basePlan,
        proposedBrief,
        proposedPlan,
        null,
        null,
        generationRequestKey,
        generationRequestFingerprint,
        generationRunId,
        generationStartedAt,
        updatedAt,
        createdAt,
        updatedAt);
  }

  public LearningPlanDraftRevision withReady(LearningPlanDraftPlan nextProposedPlan, Instant updatedAt) {
    return withCompiled(briefFromPlan(nextProposedPlan), nextProposedPlan, updatedAt).withReady(updatedAt);
  }

  public LearningPlanDraftRevision withFailure(String nextErrorCode, String nextErrorMessage, Instant updatedAt) {
    return new LearningPlanDraftRevision(
        id,
        proposalGroupId,
        draftId,
        userId,
        revisionNo,
        LearningPlanProposalRevisionStatus.FAILED,
        instruction,
        baseBrief,
        basePlan,
        proposedBrief,
        proposedPlan,
        nextErrorCode,
        nextErrorMessage,
        generationRequestKey,
        generationRequestFingerprint,
        generationRunId,
        generationStartedAt,
        updatedAt,
        createdAt,
        updatedAt);
  }

  /** 标记由第二阶段控制面成功受理的修订生成。 */
  public LearningPlanDraftRevision withGenerationStarted(
      String requestKey,
      String requestFingerprint,
      String runId,
      Instant startedAt
  ) {
    return new LearningPlanDraftRevision(
        id, proposalGroupId, draftId, userId, revisionNo, status, instruction, baseBrief, basePlan,
        proposedBrief, proposedPlan, errorCode, errorMessage, requestKey, requestFingerprint, runId,
        startedAt, null, createdAt, startedAt);
  }

  private static LearningPlanBrief briefFromPlan(LearningPlanDraftPlan plan) {
    if (plan == null) {
      return null;
    }
    Map<String, Object> metadata = plan.metadata();
    boolean personalizationEnabled = Boolean.TRUE.equals(
        metadata.get(LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED));
    return new LearningPlanBrief(
        plan.intent(),
        plan.objective(),
        targetProblemCount(metadata),
        plan.durationWeeks(),
        plan.level(),
        plan.weeklyHours(),
        plan.programmingLanguage(),
        plan.difficultyDistribution(),
        plan.topicPreferences(),
        plan.additionalConstraints(),
        personalizationEnabled,
        LearningPlanContentLocale.fromMetadata(metadata));
  }

  private static Integer targetProblemCount(Map<String, Object> metadata) {
    Object value = metadata.get(LearningPlanDraftMetadataKeys.TARGET_PROBLEM_COUNT);
    return value instanceof Number number ? number.intValue() : null;
  }
}
