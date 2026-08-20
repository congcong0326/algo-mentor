package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionBaseSnapshot;

public interface LearningPlanProposalRepository {

  LearningPlanProposalGroup saveGroup(LearningPlanProposalGroup group);

  Optional<LearningPlanProposalGroup> findGroupForUser(long groupId, long userId);

  default Optional<LearningPlanProposalGroup> findGroupForUserForUpdate(long groupId, long userId) {
    return findGroupForUser(groupId, userId);
  }

  Optional<LearningPlanProposalGroup> findLatestActiveGroup(
      long userId,
      LearningPlanProposalType proposalType,
      LearningPlanProposalTargetType targetType,
      long targetId);

  default LearningPlanProposalGroup discardActiveExtensionProposalGroup(
      long userId,
      long planId,
      long proposalGroupId,
      Instant updatedAt) {
    throw new UnsupportedOperationException("Discarding learning plan extension proposal groups is not supported");
  }

  LearningPlanDraftRevision saveDraftRevision(LearningPlanDraftRevision revision);

  LearningPlanExtensionRevision saveExtensionRevision(LearningPlanExtensionRevision revision);

  Optional<LearningPlanDraftRevision> findDraftRevisionForUser(long revisionId, long userId);

  default void lockDraftRevisionGenerationRequest(long userId, long draftId, String requestKey) {
    throw new UnsupportedOperationException("Learning plan draft revision generation request locking is not supported");
  }

  default Optional<LearningPlanDraftRevision> findDraftRevisionByGenerationRequestKey(
      long userId, long draftId, String requestKey) {
    return Optional.empty();
  }

  default Optional<LearningPlanDraftRevision> findDraftRevisionForUserAndDraft(
      long revisionId, long userId, long draftId) {
    return findDraftRevisionForUser(revisionId, userId).filter(revision -> revision.draftId() == draftId);
  }

  default Optional<LearningPlanDraftRevision> findDraftRevisionForUserAndDraftForUpdate(
      long revisionId, long userId, long draftId) {
    return findDraftRevisionForUserAndDraft(revisionId, userId, draftId);
  }

  default Optional<LearningPlanDraftRevision> completeDraftRevisionIfGenerating(
      LearningPlanDraftRevision revision) {
    return Optional.empty();
  }

  default Optional<LearningPlanDraftRevision> failDraftRevisionIfGenerating(
      long revisionId, long userId, long draftId, String errorCode, String errorMessage, Instant completedAt) {
    return Optional.empty();
  }

  default Optional<LearningPlanDraftRevision> supersedeDraftRevisionIfGenerating(
      long revisionId, long userId, long draftId, String errorCode, String errorMessage, Instant completedAt) {
    return Optional.empty();
  }

  default List<LearningPlanDraftRevision> findInterruptedDraftRevisionGenerations(Instant startedBefore) {
    return List.of();
  }

  default Optional<LearningPlanRevisionBaseSnapshot> findDraftOriginForUser(long draftId, long userId) {
    return Optional.empty();
  }

  default Optional<LearningPlanRevisionBaseSnapshot> findPreviousDraftRevisionBaseForUser(
      long proposalGroupId,
      int beforeRevisionNo,
      long userId
  ) {
    return Optional.empty();
  }

  Optional<LearningPlanExtensionRevision> findExtensionRevisionForUser(long revisionId, long userId);

  Optional<LearningPlanExtensionRevision> findLatestReadyExtensionRevision(long proposalGroupId);

  int nextRevisionNo(long proposalGroupId);

  List<Long> markReadyDraftRevisionsSuperseded(long proposalGroupId, long exceptRevisionId);

  List<Long> markReadyExtensionRevisionsSuperseded(long proposalGroupId, long exceptRevisionId);
}
