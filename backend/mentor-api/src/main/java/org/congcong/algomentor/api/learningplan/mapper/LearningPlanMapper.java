package org.congcong.algomentor.api.learningplan.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanDraftRevisionRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanDraftRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanExtensionRevisionRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanProposalGroupRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanRow;

@Mapper
public interface LearningPlanMapper {

  long insertDraft(LearningPlanDraftRow row);

  int updateDraft(LearningPlanDraftRow row);

  LearningPlanDraftRow findDraftByIdForUser(@Param("id") long id, @Param("userId") long userId);

  LearningPlanDraftRow findDraftByIdForUserForUpdate(@Param("id") long id, @Param("userId") long userId);

  long insertPlan(LearningPlanRow row);

  int updatePlanSnapshot(LearningPlanRow row);

  int deletePlanPhases(@Param("planId") long planId);

  int insertPlanPhase(
      @Param("planId") long planId,
      @Param("phaseIndex") int phaseIndex,
      @Param("title") String title,
      @Param("durationWeeks") int durationWeeks,
      @Param("focus") String focus
  );

  int insertPlanProblem(
      @Param("planId") long planId,
      @Param("phaseIndex") int phaseIndex,
      @Param("slug") String slug,
      @Param("frontendId") Integer frontendId,
      @Param("title") String title,
      @Param("titleCn") String titleCn,
      @Param("difficulty") String difficulty,
      @Param("reason") String reason,
      @Param("sortOrder") int sortOrder
  );

  List<LearningPlanRow> findPlansByUserId(@Param("userId") long userId);

  List<LearningPlanRow> findPlansByUserIdPage(
      @Param("userId") long userId,
      @Param("limit") int limit,
      @Param("offset") int offset);

  long countPlansByUserId(@Param("userId") long userId);

  long countPlansByUserIdAndStatus(@Param("userId") long userId, @Param("status") String status);

  Instant findLatestPlanCreatedAtByUserId(@Param("userId") long userId);

  LearningPlanRow findPlanByIdForUser(@Param("id") long id, @Param("userId") long userId);

  LearningPlanRow findPlanByIdForUserForUpdate(@Param("id") long id, @Param("userId") long userId);

  int clearConfirmedPlanReferences(@Param("userId") long userId, @Param("planId") long planId);

  int deletePlanByIdForUser(@Param("id") long id, @Param("userId") long userId);

  long insertProposalGroup(LearningPlanProposalGroupRow row);

  int updateProposalGroup(LearningPlanProposalGroupRow row);

  LearningPlanProposalGroupRow findProposalGroupForUser(@Param("id") long id, @Param("userId") long userId);

  LearningPlanProposalGroupRow lockProposalGroupForUpdate(@Param("id") long id, @Param("userId") long userId);

  LearningPlanProposalGroupRow lockProposalGroupByIdForUpdate(@Param("id") long id);

  LearningPlanProposalGroupRow findLatestActiveProposalGroup(
      @Param("userId") long userId,
      @Param("proposalType") String proposalType,
      @Param("targetType") String targetType,
      @Param("targetId") long targetId);

  long insertDraftRevision(LearningPlanDraftRevisionRow row);

  int updateDraftRevision(LearningPlanDraftRevisionRow row);

  LearningPlanDraftRevisionRow findDraftRevisionForUser(@Param("id") long id, @Param("userId") long userId);

  long insertExtensionRevision(LearningPlanExtensionRevisionRow row);

  int updateExtensionRevision(LearningPlanExtensionRevisionRow row);

  LearningPlanExtensionRevisionRow findExtensionRevisionForUser(@Param("id") long id, @Param("userId") long userId);

  LearningPlanExtensionRevisionRow findLatestReadyExtensionRevision(@Param("proposalGroupId") long proposalGroupId);

  int nextDraftRevisionNo(@Param("proposalGroupId") long proposalGroupId);

  int nextExtensionRevisionNo(@Param("proposalGroupId") long proposalGroupId);

  List<Long> markReadyDraftRevisionsSuperseded(
      @Param("proposalGroupId") long proposalGroupId,
      @Param("exceptRevisionId") long exceptRevisionId,
      @Param("updatedAt") Instant updatedAt);

  List<Long> markReadyExtensionRevisionsSuperseded(
      @Param("proposalGroupId") long proposalGroupId,
      @Param("exceptRevisionId") long exceptRevisionId,
      @Param("updatedAt") Instant updatedAt);

  Integer findMaxPhaseIndex(@Param("planId") long planId);

  int updatePlanJsonSnapshot(
      @Param("planId") long planId,
      @Param("userId") long userId,
      @Param("title") String title,
      @Param("planJson") JsonNode planJson,
      @Param("updatedAt") Instant updatedAt);
}
