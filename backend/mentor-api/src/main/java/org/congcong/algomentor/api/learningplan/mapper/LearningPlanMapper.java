package org.congcong.algomentor.api.learningplan.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanDraftRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanRow;

@Mapper
public interface LearningPlanMapper {

  int insertDraft(LearningPlanDraftRow row);

  int updateDraft(LearningPlanDraftRow row);

  LearningPlanDraftRow findDraftByIdForUser(@Param("id") long id, @Param("userId") long userId);

  int insertPlan(LearningPlanRow row);

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

  int clearConfirmedPlanReferences(@Param("userId") long userId, @Param("planId") long planId);

  int deletePlanByIdForUser(@Param("id") long id, @Param("userId") long userId);
}
