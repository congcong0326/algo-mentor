package org.congcong.algomentor.api.learningplan.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanDraftRevisionRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanExtensionRevisionRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanProposalGroupRow;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalTargetType;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MyBatisLearningPlanProposalRepositoryTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-01-02T00:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void insertDraftRevisionLocksGroupAndAllocatesFreshRevisionNumber() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanProposalRepository repository = new MyBatisLearningPlanProposalRepository(mapper, objectMapper);
    when(mapper.lockProposalGroupForUpdate(20, 7)).thenReturn(group(
        LearningPlanProposalType.DRAFT_REVISION,
        LearningPlanProposalTargetType.DRAFT,
        30));
    when(mapper.nextDraftRevisionNo(20)).thenReturn(5);
    when(mapper.nextExtensionRevisionNo(20)).thenReturn(2);
    when(mapper.insertDraftRevision(any())).thenReturn(101L);
    when(mapper.findDraftRevisionForUser(101, 7)).thenReturn(draftRow(101L, 5, 30));

    LearningPlanDraftRevision saved = repository.saveDraftRevision(draftRevision(null, 99, 30));

    assertThat(saved.revisionNo()).isEqualTo(5);
    verify(mapper).lockProposalGroupForUpdate(20, 7);
    ArgumentCaptor<LearningPlanDraftRevisionRow> row = ArgumentCaptor.forClass(LearningPlanDraftRevisionRow.class);
    verify(mapper).insertDraftRevision(row.capture());
    assertThat(row.getValue().revisionNo()).isEqualTo(5);
  }

  @Test
  void insertExtensionRevisionLocksGroupAndAllocatesFreshRevisionNumber() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanProposalRepository repository = new MyBatisLearningPlanProposalRepository(mapper, objectMapper);
    when(mapper.lockProposalGroupForUpdate(20, 7)).thenReturn(group(
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.PLAN,
        40));
    when(mapper.nextDraftRevisionNo(20)).thenReturn(3);
    when(mapper.nextExtensionRevisionNo(20)).thenReturn(6);
    when(mapper.insertExtensionRevision(any())).thenReturn(201L);
    when(mapper.findExtensionRevisionForUser(201, 7)).thenReturn(extensionRow(201L, 6, 40));

    LearningPlanExtensionRevision saved = repository.saveExtensionRevision(extensionRevision(null, 99, 40));

    assertThat(saved.revisionNo()).isEqualTo(6);
    verify(mapper).lockProposalGroupForUpdate(20, 7);
    ArgumentCaptor<LearningPlanExtensionRevisionRow> row =
        ArgumentCaptor.forClass(LearningPlanExtensionRevisionRow.class);
    verify(mapper).insertExtensionRevision(row.capture());
    assertThat(row.getValue().revisionNo()).isEqualTo(6);
  }

  @Test
  void insertDraftRevisionRejectsMismatchedProposalGroupTarget() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanProposalRepository repository = new MyBatisLearningPlanProposalRepository(mapper, objectMapper);
    when(mapper.lockProposalGroupForUpdate(20, 7)).thenReturn(group(
        LearningPlanProposalType.DRAFT_REVISION,
        LearningPlanProposalTargetType.DRAFT,
        31));

    assertThatThrownBy(() -> repository.saveDraftRevision(draftRevision(null, 1, 30)))
        .isInstanceOf(LearningPlanException.class)
        .extracting("code")
        .isEqualTo("LEARNING_PLAN_PROPOSAL_GROUP_INVALID");
  }

  @Test
  void insertExtensionRevisionRejectsMismatchedProposalGroupTarget() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanProposalRepository repository = new MyBatisLearningPlanProposalRepository(mapper, objectMapper);
    when(mapper.lockProposalGroupForUpdate(20, 7)).thenReturn(group(
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.PLAN,
        41));

    assertThatThrownBy(() -> repository.saveExtensionRevision(extensionRevision(null, 1, 40)))
        .isInstanceOf(LearningPlanException.class)
        .extracting("code")
        .isEqualTo("LEARNING_PLAN_PROPOSAL_GROUP_INVALID");
  }

  @Test
  void nextRevisionNoLocksProposalGroupBeforeComputingMax() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanProposalRepository repository = new MyBatisLearningPlanProposalRepository(mapper, objectMapper);
    when(mapper.lockProposalGroupByIdForUpdate(20)).thenReturn(group(
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.PLAN,
        40));
    when(mapper.nextDraftRevisionNo(20)).thenReturn(4);
    when(mapper.nextExtensionRevisionNo(20)).thenReturn(7);

    int revisionNo = repository.nextRevisionNo(20);

    assertThat(revisionNo).isEqualTo(7);
    verify(mapper).lockProposalGroupByIdForUpdate(20);
  }

  @Test
  void updateDraftRevisionRejectsMovingPersistedRevisionToAnotherDraft() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanProposalRepository repository = new MyBatisLearningPlanProposalRepository(mapper, objectMapper);
    when(mapper.findDraftRevisionForUser(101, 7)).thenReturn(draftRow(101L, 3, 30));
    when(mapper.lockProposalGroupForUpdate(20, 7)).thenReturn(group(
        LearningPlanProposalType.DRAFT_REVISION,
        LearningPlanProposalTargetType.DRAFT,
        31));

    assertThatThrownBy(() -> repository.saveDraftRevision(draftRevision(101L, 3, 31)))
        .isInstanceOf(LearningPlanException.class)
        .extracting("code")
        .isEqualTo("LEARNING_PLAN_PROPOSAL_REVISION_INVALID");
  }

  private LearningPlanDraftRevision draftRevision(Long id, int revisionNo, long draftId) {
    return new LearningPlanDraftRevision(
        id,
        20,
        draftId,
        7,
        revisionNo,
        LearningPlanProposalRevisionStatus.GENERATING,
        "revise",
        plan(),
        null,
        null,
        null,
        CREATED_AT,
        UPDATED_AT);
  }

  private LearningPlanExtensionRevision extensionRevision(Long id, int revisionNo, long planId) {
    return new LearningPlanExtensionRevision(
        id,
        20,
        planId,
        7,
        revisionNo,
        LearningPlanProposalRevisionStatus.GENERATING,
        "extend",
        plan(),
        Map.of(),
        2,
        null,
        null,
        null,
        null,
        null,
        CREATED_AT,
        UPDATED_AT);
  }

  private LearningPlanProposalGroupRow group(
      LearningPlanProposalType proposalType,
      LearningPlanProposalTargetType targetType,
      long targetId) {
    return new LearningPlanProposalGroupRow(
        20L,
        7L,
        proposalType.name(),
        targetType.name(),
        targetId,
        LearningPlanProposalGroupStatus.ACTIVE.name(),
        "instruction",
        null,
        CREATED_AT,
        UPDATED_AT);
  }

  private LearningPlanDraftRevisionRow draftRow(Long id, int revisionNo, long draftId) {
    return new LearningPlanDraftRevisionRow(
        id,
        20L,
        draftId,
        7L,
        revisionNo,
        LearningPlanProposalRevisionStatus.GENERATING.name(),
        "revise",
        objectMapper.valueToTree(plan()),
        null,
        null,
        null,
        CREATED_AT,
        UPDATED_AT);
  }

  private LearningPlanExtensionRevisionRow extensionRow(Long id, int revisionNo, long planId) {
    return new LearningPlanExtensionRevisionRow(
        id,
        20L,
        planId,
        7L,
        revisionNo,
        LearningPlanProposalRevisionStatus.GENERATING.name(),
        "extend",
        objectMapper.valueToTree(plan()),
        objectMapper.valueToTree(Map.of()),
        2,
        null,
        null,
        null,
        null,
        null,
        CREATED_AT,
        UPDATED_AT);
  }

  private LearningPlanDraftPlan plan() {
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
        List.of(new LearningPlanPhaseDraft(1, "phase", 1, "focus", List.of(), List.of(), List.of(), "review", List.of())),
        null);
  }
}
