package org.congcong.algomentor.api.learningplan.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanDraftOriginRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanDraftRevisionRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanExtensionRevisionRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanProposalGroupRow;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
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
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionBaseSnapshot;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationConstants;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MyBatisLearningPlanProposalRepositoryTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-01-02T00:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void findGroupForUserForUpdateUsesLockingMapperRead() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanProposalRepository repository = new MyBatisLearningPlanProposalRepository(mapper, objectMapper);
    when(mapper.lockProposalGroupForUpdate(20, 7)).thenReturn(group(
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.PLAN,
        40));

    Optional<?> result = repository.findGroupForUserForUpdate(20, 7);

    assertThat(result).isPresent();
    verify(mapper).lockProposalGroupForUpdate(20, 7);
  }

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
    assertThat(row.getValue().baseBriefJson().path("objective").asText())
        .isEqualTo("Prepare for Java interviews");
    assertSnapshotJson(row.getValue().basePlanJson(), "Base plan", "Prepare for Java interviews");
    assertThat(row.getValue().proposedBriefJson().path("objective").asText())
        .isEqualTo("Refine Java interview practice");
    assertSnapshotJson(row.getValue().proposedPlanJson(), "Proposed plan", "Refine Java interview practice");
    assertThat(saved.baseBrief().objective()).isEqualTo("Prepare for Java interviews");
    assertThat(saved.basePlan().objective()).isEqualTo("Prepare for Java interviews");
    assertThat(saved.proposedBrief().objective()).isEqualTo("Refine Java interview practice");
    assertThat(saved.proposedPlan().objective()).isEqualTo("Refine Java interview practice");
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
    assertSnapshotJson(row.getValue().basePlanJson(), "Extension base plan", "Prepare for Java interviews");
    assertThat(saved.basePlan().objective()).isEqualTo("Prepare for Java interviews");
    assertThat(saved.basePlan().difficultyDistribution()).isEqualTo(
        new org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution(25, 55, 20));
  }

  @Test
  void findDraftOriginReturnsTheFirstCompleteDraftSnapshot() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanProposalRepository repository = new MyBatisLearningPlanProposalRepository(mapper, objectMapper);
    LearningPlanDraftPlan originPlan = plan("AI original plan", "Build graph fundamentals");
    LearningPlanDraftRevision revision = draftRevision(null, 1, 30);
    when(mapper.findDraftOriginForUser(30, 7)).thenReturn(new LearningPlanDraftOriginRow(
        objectMapper.valueToTree(revision.baseBrief()),
        objectMapper.valueToTree(originPlan)));

    Optional<LearningPlanRevisionBaseSnapshot> result = repository.findDraftOriginForUser(30, 7);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().baseBrief()).isEqualTo(revision.baseBrief());
    assertThat(result.orElseThrow().basePlan()).isEqualTo(originPlan);
  }

  @Test
  void findPreviousDraftRevisionBaseUsesOnlyTheTrustedGroupAndRevisionBoundary() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanProposalRepository repository = new MyBatisLearningPlanProposalRepository(mapper, objectMapper);
    when(mapper.findPreviousDraftRevisionForUser(20, 5, 7)).thenReturn(draftRow(100L, 4, 30));

    Optional<LearningPlanRevisionBaseSnapshot> result = repository.findPreviousDraftRevisionBaseForUser(20, 5, 7);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().basePlan().title()).isEqualTo("Base plan");
    verify(mapper).findPreviousDraftRevisionForUser(20, 5, 7);
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
  void supersedingReadyDraftRevisionsPassesTheSafeTerminalDetailsToTheMapper() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanProposalRepository repository = new MyBatisLearningPlanProposalRepository(mapper, objectMapper);
    when(mapper.markReadyDraftRevisionsSuperseded(
        org.mockito.ArgumentMatchers.eq(20L),
        org.mockito.ArgumentMatchers.eq(101L),
        org.mockito.ArgumentMatchers.eq(LearningPlanDraftRevisionGenerationConstants.SUPERSEDED_CODE),
        org.mockito.ArgumentMatchers.eq(LearningPlanDraftRevisionGenerationConstants.SUPERSEDED_MESSAGE),
        any(),
        any())).thenReturn(List.of(100L));

    List<Long> superseded = repository.markReadyDraftRevisionsSuperseded(20, 101);

    assertThat(superseded).containsExactly(100L);
    ArgumentCaptor<Instant> completedAt = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> updatedAt = ArgumentCaptor.forClass(Instant.class);
    verify(mapper).markReadyDraftRevisionsSuperseded(
        org.mockito.ArgumentMatchers.eq(20L),
        org.mockito.ArgumentMatchers.eq(101L),
        org.mockito.ArgumentMatchers.eq(LearningPlanDraftRevisionGenerationConstants.SUPERSEDED_CODE),
        org.mockito.ArgumentMatchers.eq(LearningPlanDraftRevisionGenerationConstants.SUPERSEDED_MESSAGE),
        completedAt.capture(),
        updatedAt.capture());
    assertThat(completedAt.getValue()).isEqualTo(updatedAt.getValue());
  }

  @Test
  void discardActiveExtensionProposalGroupUsesStatusGuardedMapperUpdate() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanProposalRepository repository = new MyBatisLearningPlanProposalRepository(mapper, objectMapper);
    when(mapper.discardActiveExtensionProposalGroup(
        7,
        40,
        20,
        LearningPlanProposalGroupStatus.ACTIVE.name(),
        LearningPlanProposalGroupStatus.DISCARDED.name(),
        UPDATED_AT)).thenReturn(1);
    when(mapper.findProposalGroupForUser(20, 7)).thenReturn(group(
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.PLAN,
        40,
        LearningPlanProposalGroupStatus.DISCARDED));

    var result = repository.discardActiveExtensionProposalGroup(7, 40, 20, UPDATED_AT);

    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(LearningPlanProposalGroupStatus.DISCARDED);
    verify(mapper).discardActiveExtensionProposalGroup(
        7,
        40,
        20,
        LearningPlanProposalGroupStatus.ACTIVE.name(),
        LearningPlanProposalGroupStatus.DISCARDED.name(),
        UPDATED_AT);
  }

  @Test
  void discardActiveExtensionProposalGroupReturnsNullWhenStatusGuardMisses() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanProposalRepository repository = new MyBatisLearningPlanProposalRepository(mapper, objectMapper);
    when(mapper.discardActiveExtensionProposalGroup(
        7,
        40,
        20,
        LearningPlanProposalGroupStatus.ACTIVE.name(),
        LearningPlanProposalGroupStatus.DISCARDED.name(),
        UPDATED_AT)).thenReturn(0);

    var result = repository.discardActiveExtensionProposalGroup(7, 40, 20, UPDATED_AT);

    assertThat(result).isNull();
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
        plan("Base plan", "Prepare for Java interviews"),
        plan("Proposed plan", "Refine Java interview practice"),
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
        plan("Extension base plan", "Prepare for Java interviews"),
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
    return group(proposalType, targetType, targetId, LearningPlanProposalGroupStatus.ACTIVE);
  }

  private LearningPlanProposalGroupRow group(
      LearningPlanProposalType proposalType,
      LearningPlanProposalTargetType targetType,
      long targetId,
      LearningPlanProposalGroupStatus status) {
    return new LearningPlanProposalGroupRow(
        20L,
        7L,
        proposalType.name(),
        targetType.name(),
        targetId,
        status.name(),
        "instruction",
        null,
        CREATED_AT,
        UPDATED_AT);
  }

  private LearningPlanDraftRevisionRow draftRow(Long id, int revisionNo, long draftId) {
    LearningPlanDraftPlan basePlan = plan("Base plan", "Prepare for Java interviews");
    LearningPlanDraftPlan proposedPlan = plan("Proposed plan", "Refine Java interview practice");
    LearningPlanDraftRevision revision = new LearningPlanDraftRevision(
        id,
        20,
        draftId,
        7,
        revisionNo,
        LearningPlanProposalRevisionStatus.GENERATING,
        "revise",
        basePlan,
        proposedPlan,
        null,
        null,
        CREATED_AT,
        UPDATED_AT);
    return new LearningPlanDraftRevisionRow(
        id,
        20L,
        draftId,
        7L,
        revisionNo,
        LearningPlanProposalRevisionStatus.GENERATING.name(),
        "revise",
        objectMapper.valueToTree(revision.baseBrief()),
        objectMapper.valueToTree(basePlan),
        objectMapper.valueToTree(revision.proposedBrief()),
        objectMapper.valueToTree(proposedPlan),
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
        objectMapper.valueToTree(plan("Extension base plan", "Prepare for Java interviews")),
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

  private LearningPlanDraftPlan plan(String title, String objective) {
    return new LearningPlanDraftPlan(
        title,
        "summary",
        LearningPlanIntent.PRACTICE_GOAL,
        objective,
        4,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "java",
        new org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution(25, 55, 20),
        List.of("array"),
        "Reserve one weekly review session.",
        List.of(new LearningPlanPhaseDraft(1, "phase", 1, "focus", List.of())),
        Map.of(
            LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "en-US",
            LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, true));
  }

  private void assertSnapshotJson(JsonNode snapshot, String title, String objective) {
    assertThat(snapshot.has("objective")).isTrue();
    assertThat(snapshot.path("title").asText()).isEqualTo(title);
    assertThat(snapshot.path("objective").asText()).isEqualTo(objective);
    assertThat(snapshot.has("difficultyDistribution")).isTrue();
    assertThat(snapshot.path("difficultyDistribution").path("easyPercent").asInt()).isEqualTo(25);
    assertThat(snapshot.path("difficultyDistribution").path("mediumPercent").asInt()).isEqualTo(55);
    assertThat(snapshot.path("difficultyDistribution").path("hardPercent").asInt()).isEqualTo(20);
    assertThat(snapshot.has("additionalConstraints")).isTrue();
    assertThat(snapshot.path("additionalConstraints").asText()).isEqualTo("Reserve one weekly review session.");
    assertThat(snapshot.path("metadata").path(LearningPlanDraftMetadataKeys.CONTENT_LOCALE).asText()).isEqualTo("en-US");
    assertThat(snapshot.path("metadata").path(LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED).asBoolean()).isTrue();
  }
}
