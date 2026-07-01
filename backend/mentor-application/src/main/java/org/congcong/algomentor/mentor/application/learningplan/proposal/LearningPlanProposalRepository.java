package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.util.List;
import java.util.Optional;

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

  LearningPlanDraftRevision saveDraftRevision(LearningPlanDraftRevision revision);

  LearningPlanExtensionRevision saveExtensionRevision(LearningPlanExtensionRevision revision);

  Optional<LearningPlanDraftRevision> findDraftRevisionForUser(long revisionId, long userId);

  Optional<LearningPlanExtensionRevision> findExtensionRevisionForUser(long revisionId, long userId);

  Optional<LearningPlanExtensionRevision> findLatestReadyExtensionRevision(long proposalGroupId);

  int nextRevisionNo(long proposalGroupId);

  List<Long> markReadyDraftRevisionsSuperseded(long proposalGroupId, long exceptRevisionId);

  List<Long> markReadyExtensionRevisionsSuperseded(long proposalGroupId, long exceptRevisionId);
}
