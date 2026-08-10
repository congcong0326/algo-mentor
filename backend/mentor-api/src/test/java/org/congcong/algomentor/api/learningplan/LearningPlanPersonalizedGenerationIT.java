package org.congcong.algomentor.api.learningplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper;
import org.congcong.algomentor.api.learningplan.repository.MyBatisLearningPlanProposalRepository;
import org.congcong.algomentor.api.learningplan.repository.MyBatisLearningPlanRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
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
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionDraft;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroup;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalTargetType;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalType;
import org.junit.jupiter.api.Test;

class LearningPlanPersonalizedGenerationIT extends PostgresIntegrationTestSupport {

  private static final Instant CREATED_AT = Instant.parse("2026-08-03T05:30:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-08-03T05:45:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void freezesAiCreatedFirstCompleteDraftAsTheSourceIndependentOrigin() throws Exception {
    migrateLatest();
    long userId = insertUser();
    Repositories repositories = repositories();
    LearningPlanBrief originalBrief = brief("AI 首版学习计划", 4, 25, 55, 20);
    LearningPlanDraftPlan originalPlan = plan("AI 首版草稿", "AI 首版学习计划", 4, 25, 55, 20);
    LearningPlanDraft draft = repositories.plans.save(new LearningPlanDraft(
        null,
        userId,
        LearningPlanDraftStatus.GENERATED,
        originalBrief,
        List.of("由 AI 创建第一版草稿"),
        List.of(),
        "已生成学习计划草案。",
        originalPlan,
        null,
        CREATED_AT.plusSeconds(86_400),
        CREATED_AT,
        UPDATED_AT));

    assertThat(repositories.proposals.findDraftOriginForUser(draft.id(), userId))
        .get()
        .satisfies(origin -> {
          assertThat(origin.baseBrief()).isEqualTo(originalBrief);
          assertThat(origin.basePlan()).isEqualTo(originalPlan);
        });

    LearningPlanBrief revisedBrief = brief("修订后的学习计划", 4, 40, 60, 0);
    LearningPlanDraftPlan revisedPlan = plan("删减后的草稿", "修订后的学习计划", 4, 40, 60, 0);
    repositories.plans.save(draft.withGeneratedRevision(
        revisedBrief,
        List.of("删除中等与困难题"),
        revisedPlan,
        UPDATED_AT.plusSeconds(60)));

    assertThat(repositories.proposals.findDraftOriginForUser(draft.id(), userId))
        .get()
        .satisfies(origin -> {
          assertThat(origin.baseBrief()).isEqualTo(originalBrief);
          assertThat(origin.basePlan()).isEqualTo(originalPlan);
        });
  }

  @Test
  void roundTripsDraftFormalRevisionAndExtensionSnapshotsWithoutLegacyFields() throws Exception {
    migrateLatest();
    long userId = insertUser();
    Repositories repositories = repositories();

    LearningPlanDraftPlan draftSnapshot = plan("创建草案", "完成算法面试准备", 4, 25, 55, 20);
    LearningPlanDraft draft = repositories.plans.save(new LearningPlanDraft(
        null,
        userId,
        LearningPlanDraftStatus.GENERATED,
        brief("完成算法面试准备", 4, 25, 55, 20),
        List.of("生成学习计划"),
        List.of(),
        "已生成学习计划草案。",
        draftSnapshot,
        null,
        CREATED_AT.plusSeconds(86_400),
        CREATED_AT,
        UPDATED_AT));

    LearningPlanDraftPlan formalSnapshot = plan("正式计划", "完成算法面试准备", 4, 25, 55, 20);
    LearningPlan plan = repositories.plans.save(new LearningPlan(
        null,
        userId,
        LearningPlanStatus.ACTIVE,
        formalSnapshot,
        CREATED_AT,
        UPDATED_AT));

    LearningPlanProposalGroup revisionGroup = repositories.proposals.saveGroup(new LearningPlanProposalGroup(
        null,
        userId,
        LearningPlanProposalType.DRAFT_REVISION,
        LearningPlanProposalTargetType.DRAFT,
        draft.id(),
        LearningPlanProposalGroupStatus.ACTIVE,
        "将周期调整为六周，并提高困难题比例。",
        null,
        CREATED_AT,
        UPDATED_AT));
    LearningPlanDraftPlan revisedSnapshot = plan("修订草案", "完成算法面试冲刺", 6, 15, 50, 35);
    LearningPlanDraftRevision revision = repositories.proposals.saveDraftRevision(new LearningPlanDraftRevision(
        null,
        revisionGroup.id(),
        draft.id(),
        userId,
        1,
        LearningPlanProposalRevisionStatus.READY,
        "更新周期、目标和难度。",
        draftSnapshot,
        revisedSnapshot,
        null,
        null,
        CREATED_AT,
        UPDATED_AT));

    LearningPlanProposalGroup extensionGroup = repositories.proposals.saveGroup(new LearningPlanProposalGroup(
        null,
        userId,
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.PLAN,
        plan.id(),
        LearningPlanProposalGroupStatus.ACTIVE,
        "追加图论阶段。",
        null,
        CREATED_AT,
        UPDATED_AT));
    LearningPlanExtensionDraft previousExtension = extension("上一轮扩展", "补充复盘");
    LearningPlanExtensionDraft proposedExtension = extension("本轮扩展", "图论与最短路");
    LearningPlanExtensionRevision extension = repositories.proposals.saveExtensionRevision(
        new LearningPlanExtensionRevision(
            null,
            extensionGroup.id(),
            plan.id(),
            userId,
            1,
            LearningPlanProposalRevisionStatus.READY,
            "追加一个图论阶段。",
            formalSnapshot,
            Map.of("completedPhaseCount", 1, "completedProblemCount", 2),
            1,
            previousExtension,
            proposedExtension,
            null,
            null,
            null,
            CREATED_AT,
            UPDATED_AT));

    assertThat(draft.draftPlan()).isEqualTo(draftSnapshot);
    assertThat(repositories.plans.findPlanByIdForUser(plan.id(), userId))
        .get()
        .extracting(LearningPlan::plan)
        .isEqualTo(formalSnapshot);
    assertThat(repositories.proposals.findDraftRevisionForUser(revision.id(), userId))
        .get()
        .satisfies(saved -> {
          assertThat(saved.basePlan()).isEqualTo(draftSnapshot);
          assertThat(saved.proposedPlan()).isEqualTo(revisedSnapshot);
        });
    assertThat(repositories.proposals.findExtensionRevisionForUser(extension.id(), userId))
        .get()
        .satisfies(saved -> {
          assertThat(saved.basePlan()).isEqualTo(formalSnapshot);
          assertThat(saved.previousExtension()).isEqualTo(previousExtension);
          assertThat(saved.proposedExtension()).isEqualTo(proposedExtension);
        });

    assertBriefJson(queryString(
        "SELECT command_json::text FROM learning_plan_draft WHERE id = ?", draft.id()));
    assertPlanSnapshotJson(queryString(
        "SELECT draft_plan_json::text FROM learning_plan_draft WHERE id = ?", draft.id()));
    assertPlanSnapshotJson(queryString(
        "SELECT plan_json::text FROM learning_plan WHERE id = ?", plan.id()));
    assertPlanSnapshotJson(queryString(
        "SELECT base_plan_json::text FROM learning_plan_draft_revision WHERE id = ?", revision.id()));
    assertPlanSnapshotJson(queryString(
        "SELECT proposed_plan_json::text FROM learning_plan_draft_revision WHERE id = ?", revision.id()));
    assertPlanSnapshotJson(queryString(
        "SELECT base_plan_json::text FROM learning_plan_extension_revision WHERE id = ?", extension.id()));
    assertExtensionJson(queryString(
        "SELECT previous_extension_json::text FROM learning_plan_extension_revision WHERE id = ?", extension.id()));
    assertExtensionJson(queryString(
        "SELECT proposed_extension_json::text FROM learning_plan_extension_revision WHERE id = ?", extension.id()));
  }

  private Repositories repositories() throws Exception {
    LearningPlanMapper mapper = sqlSessionTemplate("mapper/learningplan/LearningPlanMapper.xml")
        .getMapper(LearningPlanMapper.class);
    return new Repositories(
        new MyBatisLearningPlanRepository(mapper, objectMapper),
        new MyBatisLearningPlanProposalRepository(mapper, objectMapper));
  }

  private LearningPlanBrief brief(String objective, int durationWeeks, int easy, int medium, int hard) {
    return new LearningPlanBrief(
        LearningPlanIntent.INTERVIEW_SPRINT,
        objective,
        durationWeeks,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "Java",
        new LearningPlanDifficultyDistribution(easy, medium, hard),
        List.of("array", "graph"),
        "每周保留一次复盘。",
        true,
        LearningPlanContentLocale.ZH_CN);
  }

  private LearningPlanDraftPlan plan(
      String title,
      String objective,
      int durationWeeks,
      int easy,
      int medium,
      int hard
  ) {
    return new LearningPlanDraftPlan(
        title,
        "个性化计划摘要",
        LearningPlanIntent.INTERVIEW_SPRINT,
        objective,
        durationWeeks,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "Java",
        new LearningPlanDifficultyDistribution(easy, medium, hard),
        List.of("array", "graph"),
        "每周保留一次复盘。",
        List.of(phase(1, "基础训练")),
        Map.of(
            LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "zh-CN",
            LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, true));
  }

  private LearningPlanExtensionDraft extension(String summary, String focus) {
    return new LearningPlanExtensionDraft(
        summary,
        List.of(phase(1, focus)),
        Map.of(LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, true));
  }

  private LearningPlanPhaseDraft phase(int phaseIndex, String focus) {
    return new LearningPlanPhaseDraft(
        phaseIndex,
        "阶段 " + phaseIndex,
        1,
        focus,
        List.of());
  }

  private void assertBriefJson(String json) throws Exception {
    assertThat(json).isNotBlank();
    assertThat(fieldNames(objectMapper.readTree(json))).containsExactlyInAnyOrder(
        "intent",
        "objective",
        "durationWeeks",
        "level",
        "weeklyHours",
        "programmingLanguage",
        "difficultyDistribution",
        "topicPreferences",
        "additionalConstraints",
        "personalizationEnabled",
        "contentLocale");
  }

  private void assertPlanSnapshotJson(String json) throws Exception {
    assertThat(json).isNotBlank();
    assertThat(fieldNames(objectMapper.readTree(json))).containsExactlyInAnyOrder(
        "title",
        "summary",
        "intent",
        "objective",
        "durationWeeks",
        "level",
        "weeklyHours",
        "programmingLanguage",
        "difficultyDistribution",
        "topicPreferences",
        "additionalConstraints",
        "phases",
        "metadata");
  }

  private void assertExtensionJson(String json) throws Exception {
    assertThat(json).isNotBlank();
    assertThat(fieldNames(objectMapper.readTree(json))).containsExactlyInAnyOrder(
        "summary", "newPhases", "metadata");
  }

  private List<String> fieldNames(JsonNode node) {
    List<String> names = new java.util.ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private record Repositories(
      MyBatisLearningPlanRepository plans,
      MyBatisLearningPlanProposalRepository proposals
  ) {
  }
}
