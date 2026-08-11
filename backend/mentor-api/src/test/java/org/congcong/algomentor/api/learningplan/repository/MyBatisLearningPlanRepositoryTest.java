package org.congcong.algomentor.api.learningplan.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanDraftRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanRow;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class MyBatisLearningPlanRepositoryTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-01-02T00:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void findDraftByIdForUserForUpdateUsesLockingMapperRead() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanRepository repository = new MyBatisLearningPlanRepository(mapper, objectMapper);
    when(mapper.findDraftByIdForUserForUpdate(12, 7)).thenReturn(draftRow(plan(List.of(phase(1, "base", "two-sum")))));

    Optional<LearningPlanDraft> result = repository.findDraftByIdForUserForUpdate(12, 7);

    assertThat(result).isPresent();
    verify(mapper).findDraftByIdForUserForUpdate(12, 7);
  }

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
  void createDraftConsumesDailyQuotaAndInsertsInOneRepositoryOperation() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanRepository repository = new MyBatisLearningPlanRepository(mapper, objectMapper);
    LearningPlanDraftPlan snapshot = plan(List.of(phase(1, "base", "two-sum")));
    LearningPlanDraft draft = new LearningPlanDraft(
        null,
        7L,
        LearningPlanDraftStatus.GENERATED,
        brief(),
        List.of(),
        List.of(),
        "assistant",
        snapshot,
        null,
        CREATED_AT.plusSeconds(86_400),
        CREATED_AT,
        CREATED_AT);
    when(mapper.tryConsumeDailyDraftQuota(7L, LocalDate.parse("2026-01-01"), 5, CREATED_AT))
        .thenReturn(1);
    when(mapper.insertDraft(any())).thenReturn(12L);
    when(mapper.findDraftByIdForUser(12L, 7L)).thenReturn(draftRow(snapshot));

    Optional<LearningPlanDraft> created = repository.createWithinDailyLimit(
        draft, LocalDate.parse("2026-01-01"), 5, CREATED_AT);

    assertThat(created).isPresent();
    InOrder order = inOrder(mapper);
    order.verify(mapper).tryConsumeDailyDraftQuota(7L, LocalDate.parse("2026-01-01"), 5, CREATED_AT);
    order.verify(mapper).insertDraft(any());
  }

  @Test
  void createDraftDoesNotInsertWhenDailyQuotaIsExhausted() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanRepository repository = new MyBatisLearningPlanRepository(mapper, objectMapper);
    when(mapper.tryConsumeDailyDraftQuota(7L, LocalDate.parse("2026-01-01"), 5, CREATED_AT))
        .thenReturn(null);

    Optional<LearningPlanDraft> created = repository.createWithinDailyLimit(
        new LearningPlanDraft(
            null,
            7L,
            LearningPlanDraftStatus.COLLECTING,
            brief(),
            List.of(),
            List.of(),
            null,
            null,
            null,
            CREATED_AT.plusSeconds(86_400),
            CREATED_AT,
            CREATED_AT),
        LocalDate.parse("2026-01-01"),
        5,
        CREATED_AT);

    assertThat(created).isEmpty();
    verify(mapper, never()).insertDraft(any());
  }

  @Test
  void createPlanLocksUserAndRejectsAtSavedPlanLimit() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanRepository repository = new MyBatisLearningPlanRepository(mapper, objectMapper);
    when(mapper.countPlansByUserId(7L)).thenReturn(30L);

    Optional<LearningPlan> created = repository.createIfBelowLimit(
        new LearningPlan(
            null,
            7L,
            LearningPlanStatus.ACTIVE,
            plan(List.of(phase(1, "base", "two-sum"))),
            CREATED_AT,
            CREATED_AT),
        30);

    assertThat(created).isEmpty();
    InOrder order = inOrder(mapper);
    order.verify(mapper).lockPlanCreationForUser(7L);
    order.verify(mapper).countPlansByUserId(7L);
    verify(mapper, never()).insertPlan(any());
  }

  @Test
  void cleanupDeletesDraftProposalGroupsBeforeDraftRows() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanRepository repository = new MyBatisLearningPlanRepository(mapper, objectMapper);
    when(mapper.findExpiredDraftIdsForCleanup(CREATED_AT, 100)).thenReturn(List.of(10L, 11L));
    when(mapper.deleteDraftsByIds(List.of(10L, 11L))).thenReturn(2);

    int deleted = repository.deleteExpiredDrafts(CREATED_AT, 100);

    assertThat(deleted).isEqualTo(2);
    InOrder order = inOrder(mapper);
    order.verify(mapper).findExpiredDraftIdsForCleanup(CREATED_AT, 100);
    order.verify(mapper).deleteProposalGroupsForDrafts(List.of(10L, 11L));
    order.verify(mapper).deleteDraftsByIds(List.of(10L, 11L));
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
    assertSnapshotJson(planJson.getValue());
    LearningPlanDraftPlan snapshot = objectMapper.treeToValue(planJson.getValue(), LearningPlanDraftPlan.class);
    assertThat(snapshot.phases()).extracting(LearningPlanPhaseDraft::phaseIndex).containsExactly(1, 3, 4);
    assertThat(snapshot.objective()).isEqualTo("Prepare for Java interviews");
    assertThat(snapshot.difficultyDistribution()).isEqualTo(
        new org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution(25, 55, 20));
    assertThat(snapshot.additionalConstraints()).isEqualTo("Reserve one weekly review session.");
    assertThat(snapshot.metadata())
        .containsEntry(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "en-US")
        .containsEntry(LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, true);
  }

  @Test
  void saveDraftPersistsOnlyNewSnapshotFields() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanRepository repository = new MyBatisLearningPlanRepository(mapper, objectMapper);
    LearningPlanDraftPlan snapshot = plan(List.of(phase(1, "base", "two-sum")));
    LearningPlanDraft draft = new LearningPlanDraft(
        null,
        7L,
        LearningPlanDraftStatus.GENERATED,
        brief(),
        List.of("message"),
        List.of(),
        "assistant",
        snapshot,
        null,
        null,
        CREATED_AT,
        UPDATED_AT);
    when(mapper.insertDraft(any())).thenReturn(12L);
    when(mapper.findDraftByIdForUser(12, 7)).thenReturn(draftRow(snapshot));

    LearningPlanDraft saved = repository.save(draft);

    ArgumentCaptor<LearningPlanDraftRow> row = ArgumentCaptor.forClass(LearningPlanDraftRow.class);
    verify(mapper).insertDraft(row.capture());
    assertBriefJson(row.getValue().commandJson());
    assertSnapshotJson(row.getValue().draftPlanJson());
    assertThat(saved.brief()).isEqualTo(brief());
    assertThat(saved.draftPlan().objective()).isEqualTo("Prepare for Java interviews");
    assertThat(saved.draftPlan().difficultyDistribution()).isEqualTo(
        new org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution(25, 55, 20));
    assertThat(saved.draftPlan().additionalConstraints()).isEqualTo("Reserve one weekly review session.");
  }

  @Test
  void updateDraftPersistsResolvedBriefAndGeneratedPlanWithConsistentPlanningFields() throws Exception {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanRepository repository = new MyBatisLearningPlanRepository(mapper, objectMapper);
    LearningPlanBrief revisedBrief = new LearningPlanBrief(
        LearningPlanIntent.TOPIC_BREAKTHROUGH,
        "Master graph algorithms",
        6,
        LearningPlanLevel.ADVANCED,
        10,
        "Kotlin",
        new LearningPlanDifficultyDistribution(20, 50, 30),
        List.of("graph", "shortest-path"),
        "Reserve one graph review session.",
        true,
        LearningPlanContentLocale.EN_US);
    LearningPlanDraftPlan revisedPlan = new LearningPlanDraftPlan(
        "Graph revision",
        "Updated graph-focused plan",
        revisedBrief.intent(),
        revisedBrief.objective(),
        revisedBrief.durationWeeks(),
        revisedBrief.level(),
        revisedBrief.weeklyHours(),
        revisedBrief.programmingLanguage(),
        revisedBrief.difficultyDistribution(),
        revisedBrief.topicPreferences(),
        revisedBrief.additionalConstraints(),
        List.of(phase(1, "graph", "two-sum")),
        Map.of(
            LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "en-US",
            LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, true));
    LearningPlanDraft revisedDraft = new LearningPlanDraft(
        12L,
        7L,
        LearningPlanDraftStatus.GENERATED,
        revisedBrief,
        List.of("revision instruction"),
        List.of(),
        "assistant",
        revisedPlan,
        null,
        null,
        CREATED_AT,
        UPDATED_AT);
    when(mapper.findDraftByIdForUser(12, 7)).thenReturn(draftRow(revisedBrief, revisedPlan));

    LearningPlanDraft saved = repository.save(revisedDraft);

    ArgumentCaptor<LearningPlanDraftRow> row = ArgumentCaptor.forClass(LearningPlanDraftRow.class);
    verify(mapper).updateDraft(row.capture());
    LearningPlanBrief persistedBrief = objectMapper.treeToValue(row.getValue().commandJson(), LearningPlanBrief.class);
    LearningPlanDraftPlan persistedPlan = objectMapper.treeToValue(
        row.getValue().draftPlanJson(), LearningPlanDraftPlan.class);
    assertThat(persistedBrief).isEqualTo(revisedBrief);
    assertThat(persistedPlan.intent()).isEqualTo(persistedBrief.intent());
    assertThat(persistedPlan.objective()).isEqualTo(persistedBrief.objective());
    assertThat(persistedPlan.durationWeeks()).isEqualTo(persistedBrief.durationWeeks());
    assertThat(persistedPlan.difficultyDistribution()).isEqualTo(persistedBrief.difficultyDistribution());
    assertThat(persistedPlan.additionalConstraints()).isEqualTo(persistedBrief.additionalConstraints());
    assertThat(saved.brief()).isEqualTo(revisedBrief);
    assertThat(saved.draftPlan()).isEqualTo(revisedPlan);
  }

  @Test
  void savePlanPersistsOnlyNewSnapshotFields() {
    LearningPlanMapper mapper = mock(LearningPlanMapper.class);
    MyBatisLearningPlanRepository repository = new MyBatisLearningPlanRepository(mapper, objectMapper);
    LearningPlanDraftPlan snapshot = plan(List.of(phase(1, "base", "two-sum")));
    LearningPlan plan = new LearningPlan(null, 7L, LearningPlanStatus.ACTIVE, snapshot, CREATED_AT, UPDATED_AT);
    when(mapper.insertPlan(any())).thenReturn(12L);
    when(mapper.findPlanByIdForUser(12, 7)).thenReturn(planRow(snapshot));

    LearningPlan saved = repository.save(plan);

    ArgumentCaptor<LearningPlanRow> row = ArgumentCaptor.forClass(LearningPlanRow.class);
    verify(mapper).insertPlan(row.capture());
    assertSnapshotJson(row.getValue().planJson());
    assertThat(saved.plan().objective()).isEqualTo("Prepare for Java interviews");
    assertThat(saved.plan().metadata())
        .containsEntry(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "en-US")
        .containsEntry(LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, true);
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

  private LearningPlanDraftRow draftRow(LearningPlanDraftPlan plan) {
    return draftRow(brief(), plan);
  }

  private LearningPlanDraftRow draftRow(LearningPlanBrief brief, LearningPlanDraftPlan plan) {
    return new LearningPlanDraftRow(
        12L,
        7L,
        LearningPlanDraftStatus.GENERATED.name(),
        objectMapper.valueToTree(brief),
        objectMapper.valueToTree(List.of("message")),
        objectMapper.valueToTree(List.of()),
        "assistant",
        objectMapper.valueToTree(plan),
        null,
        UPDATED_AT,
        CREATED_AT,
        UPDATED_AT);
  }

  private LearningPlanDraftPlan plan(List<LearningPlanPhaseDraft> phases) {
    return new LearningPlanDraftPlan(
        "Plan",
        "summary",
        LearningPlanIntent.PRACTICE_GOAL,
        "Prepare for Java interviews",
        4,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "java",
        new org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution(25, 55, 20),
        List.of("array"),
        "Reserve one weekly review session.",
        phases,
        Map.of(
            LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "en-US",
            LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, true));
  }

  private LearningPlanBrief brief() {
    return new LearningPlanBrief(
        LearningPlanIntent.PRACTICE_GOAL,
        "Prepare for Java interviews",
        4,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "java",
        new LearningPlanDifficultyDistribution(25, 55, 20),
        List.of("array"),
        "Reserve one weekly review session.",
        true,
        LearningPlanContentLocale.EN_US);
  }

  private void assertBriefJson(JsonNode brief) {
    assertThat(brief.path("intent").asText()).isEqualTo("PRACTICE_GOAL");
    assertThat(brief.path("objective").asText()).isEqualTo("Prepare for Java interviews");
    assertThat(brief.path("difficultyDistribution").path("easyPercent").asInt()).isEqualTo(25);
    assertThat(brief.path("difficultyDistribution").path("mediumPercent").asInt()).isEqualTo(55);
    assertThat(brief.path("difficultyDistribution").path("hardPercent").asInt()).isEqualTo(20);
    assertThat(brief.path("additionalConstraints").asText()).isEqualTo("Reserve one weekly review session.");
    assertThat(brief.path("personalizationEnabled").asBoolean()).isTrue();
    assertThat(brief.path("contentLocale").asText()).isEqualTo("en-US");
  }

  private void assertSnapshotJson(JsonNode snapshot) {
    assertThat(snapshot.has("objective")).isTrue();
    assertThat(snapshot.path("objective").asText()).isEqualTo("Prepare for Java interviews");
    assertThat(snapshot.has("difficultyDistribution")).isTrue();
    assertThat(snapshot.path("difficultyDistribution").path("easyPercent").asInt()).isEqualTo(25);
    assertThat(snapshot.path("difficultyDistribution").path("mediumPercent").asInt()).isEqualTo(55);
    assertThat(snapshot.path("difficultyDistribution").path("hardPercent").asInt()).isEqualTo(20);
    assertThat(snapshot.has("additionalConstraints")).isTrue();
    assertThat(snapshot.path("additionalConstraints").asText()).isEqualTo("Reserve one weekly review session.");
  }

  private LearningPlanPhaseDraft phase(int phaseIndex, String title, String slug) {
    return new LearningPlanPhaseDraft(
        phaseIndex,
        title,
        1,
        "focus-" + title,
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
