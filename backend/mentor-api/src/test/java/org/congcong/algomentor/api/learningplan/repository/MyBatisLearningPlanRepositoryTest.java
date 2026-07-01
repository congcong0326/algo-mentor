package org.congcong.algomentor.api.learningplan.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanRow;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MyBatisLearningPlanRepositoryTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-01-02T00:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void findPlanByIdForUserForUpdateUsesLockingMapperRead() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanRepository repository = new MyBatisLearningPlanRepository(mapper, objectMapper);
    when(mapper.findPlanByIdForUserForUpdate(12, 7)).thenReturn(planRow(plan(List.of(phase(1, "base", "two-sum")))));

    Optional<LearningPlan> result = repository.findPlanByIdForUserForUpdate(12, 7);

    assertThat(result).isPresent();
    verify(mapper).findPlanByIdForUserForUpdate(12, 7);
  }

  @Test
  void appendPhasesLocksPlanAndReindexesNewPhasesFromCurrentMax() throws Exception {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanRepository repository = new MyBatisLearningPlanRepository(mapper, objectMapper);
    LearningPlanDraftPlan currentPlan = plan(List.of(phase(1, "base", "two-sum")));
    LearningPlanDraftPlan reloadedPlan = plan(List.of(
        phase(1, "base", "two-sum"),
        phase(3, "extension-a", "three-sum"),
        phase(4, "extension-b", "binary-search")));
    when(mapper.findPlanByIdForUserForUpdate(12, 7)).thenReturn(planRow(currentPlan));
    when(mapper.findMaxPhaseIndex(12)).thenReturn(2);
    when(mapper.findPlanByIdForUser(12, 7)).thenReturn(planRow(reloadedPlan));

    LearningPlan result = repository.appendPhases(
        7,
        12,
        List.of(phase(1, "extension-a", "three-sum"), phase(2, "extension-b", "binary-search")));

    assertThat(result.plan().phases()).extracting(LearningPlanPhaseDraft::phaseIndex).containsExactly(1, 3, 4);
    verify(mapper).findPlanByIdForUserForUpdate(12, 7);
    verify(mapper).findMaxPhaseIndex(12);
    verify(mapper).insertPlanPhase(12, 3, "extension-a", 1, "focus-extension-a");
    verify(mapper).insertPlanProblem(eq(12L), eq(3), eq("three-sum"), any(), any(), any(), any(), any(), eq(1));
    verify(mapper).insertPlanPhase(12, 4, "extension-b", 1, "focus-extension-b");
    verify(mapper).insertPlanProblem(eq(12L), eq(4), eq("binary-search"), any(), any(), any(), any(), any(), eq(1));
    verify(mapper, never()).deletePlanPhases(12);

    ArgumentCaptor<JsonNode> planJson = ArgumentCaptor.forClass(JsonNode.class);
    verify(mapper).updatePlanJsonSnapshot(eq(12L), eq(7L), eq("Plan"), planJson.capture(), any());
    LearningPlanDraftPlan snapshot = objectMapper.treeToValue(planJson.getValue(), LearningPlanDraftPlan.class);
    assertThat(snapshot.phases()).extracting(LearningPlanPhaseDraft::phaseIndex).containsExactly(1, 3, 4);
  }

  private LearningPlanRow planRow(LearningPlanDraftPlan plan) {
    return new LearningPlanRow(
        12L,
        7L,
        LearningPlanStatus.ACTIVE.name(),
        plan.title(),
        objectMapper.valueToTree(plan),
        CREATED_AT,
        UPDATED_AT);
  }

  private LearningPlanDraftPlan plan(List<LearningPlanPhaseDraft> phases) {
    return new LearningPlanDraftPlan(
        "Plan",
        "summary",
        LearningPlanIntent.PRACTICE_GOAL,
        "goal",
        4,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "java",
        LearningPlanDifficultyPreference.MIXED,
        true,
        List.of("array"),
        "profile",
        phases,
        null);
  }

  private LearningPlanPhaseDraft phase(int phaseIndex, String title, String slug) {
    return new LearningPlanPhaseDraft(
        phaseIndex,
        title,
        1,
        "focus-" + title,
        List.of(),
        List.of(),
        List.of(),
        "review",
        List.of(new LearningPlanProblemDraft(
            slug,
            1,
            title,
            title,
            "MEDIUM",
            List.of(),
            "reason",
            1)));
  }
}
