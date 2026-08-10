package org.congcong.algomentor.mentor.application.learningplan.proposal.revision;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemSearch;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroup;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalTargetType;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalType;
import org.junit.jupiter.api.Test;

class LearningPlanRevisionPipelineTest {

  private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DemoProblemCatalog catalog = new DemoProblemCatalog();

  @Test
  void inlineProjectionExposesSemanticFieldsButOmitsServerOwnedFields() {
    LearningPlanRevisionModelViewProjector projector = new LearningPlanRevisionModelViewProjector(objectMapper);

    Map<String, Object> view = projector.project(new LearningPlanRevisionBaseSnapshot(brief(), plan(2, false)));

    assertThat(view).containsEntry("projectionMode", LearningPlanRevisionToolContracts.PROJECTION_INLINE_FULL);
    Map<?, ?> projectedPlan = (Map<?, ?>) view.get("plan");
    Map<?, ?> phase = (Map<?, ?>) ((List<?>) projectedPlan.get("phases")).get(0);
    Map<?, ?> problem = (Map<?, ?>) ((List<?>) phase.get("problems")).get(0);
    assertThat(phase.keySet().stream().map(Object::toString).toList()).containsExactlyInAnyOrder(
        "ref", "title", "focus", "problemCount", "difficultyCounts", "problems");
    assertThat(phase.containsKey("phaseIndex")).isFalse();
    assertThat(phase.containsKey("durationWeeks")).isFalse();
    assertThat(problem.keySet().stream().map(Object::toString).toList())
        .containsExactlyInAnyOrder("slug", "displayTitle", "difficulty", "reason");
    assertThat(problem.keySet().stream().map(Object::toString).toList())
        .doesNotContain("frontendId", "title", "titleCn", "tags", "sortOrder");
    assertThat(projectedPlan.keySet().stream().map(Object::toString).toList())
        .doesNotContain("metadata", "intent", "objective", "durationWeeks");
  }

  @Test
  void oneHundredFortyNineProblemPlanUsesSummaryProjection() {
    LearningPlanRevisionModelViewProjector projector = new LearningPlanRevisionModelViewProjector(objectMapper);

    Map<String, Object> view = projector.project(new LearningPlanRevisionBaseSnapshot(brief(), plan(149, true)));

    assertThat(view).containsEntry(
        "projectionMode", LearningPlanRevisionToolContracts.PROJECTION_SUMMARY_WITH_TOOLS);
    Map<?, ?> projectedPlan = (Map<?, ?>) view.get("plan");
    assertThat(projectedPlan.get("problemCount")).isEqualTo(149);
    Map<?, ?> phase = (Map<?, ?>) ((List<?>) projectedPlan.get("phases")).get(0);
    assertThat(phase.get("problemCount")).isEqualTo(149);
    assertThat(phase.containsKey("problems")).isFalse();
  }

  @Test
  void toolSchemasExposeOnlyTrustedBaselineSelectorsWithoutBusinessIds() {
    TestProposalRepository repository = repository(plan(2, false));
    JsonNode queryProperties = new QueryLearningPlanRevisionAgentTool(repository)
        .spec().inputSchema().path("properties");
    JsonNode compileProperties = compileTool(repository).spec().inputSchema().path("properties");

    assertThat(queryProperties.has("baseline")).isTrue();
    assertThat(queryProperties.path("operation").path("enum").toString())
        .contains(LearningPlanRevisionToolContracts.OPERATION_READ_BASELINE_OPTIONS);
    assertThat(compileProperties.has("baseline")).isTrue();
    assertThat(fieldNames(queryProperties))
        .doesNotContain("userId", "draftId", "revisionId", "templateId");
    assertThat(fieldNames(compileProperties))
        .doesNotContain("userId", "draftId", "revisionId", "templateId");
  }

  @Test
  void queryToolPaginatesOnlyTheTrustedFrozenRevision() throws Exception {
    TestProposalRepository repository = repository(plan(149, true));
    QueryLearningPlanRevisionAgentTool tool = new QueryLearningPlanRevisionAgentTool(repository);

    JsonNode firstPage = tool.execute(objectMapper.readTree("""
        {
          "operation":"READ_PHASE",
          "baseline":null,
          "phaseRef":"phase-1",
          "phaseRefs":[],
          "slugs":[],
          "difficulty":null,
          "keyword":null,
          "cursor":null,
          "limit":30
        }
        """), context(42L, 101L));

    assertThat(firstPage.path("status").asText()).isEqualTo("OK");
    assertThat(firstPage.path("snapshotState").asText()).isEqualTo("FROZEN");
    assertThat(firstPage.path("phase").has("phaseIndex")).isFalse();
    assertThat(firstPage.path("phase").has("durationWeeks")).isFalse();
    assertThat(firstPage.path("problems")).hasSize(30);
    assertThat(firstPage.path("nextCursor").asText()).isNotBlank();

    JsonNode denied = tool.execute(objectMapper.readTree("""
        {
          "operation":"READ_PHASE",
          "baseline":null,
          "phaseRef":"phase-1",
          "phaseRefs":[],
          "slugs":[],
          "difficulty":null,
          "keyword":null,
          "cursor":null,
          "limit":30
        }
        """), context(99L, 101L));
    assertThat(denied.path("failureCode").asText()).isEqualTo("REVISION_SNAPSHOT_NOT_FOUND");
  }

  @Test
  void queryToolReportsTrustedBaselineOptionsAndRejectsCrossBaselineCursor() throws Exception {
    TestProposalRepository repository = repository(
        plan(4, true),
        plan(18, true),
        plan(17, true),
        3);
    QueryLearningPlanRevisionAgentTool tool = new QueryLearningPlanRevisionAgentTool(repository);

    JsonNode options = tool.execute(objectMapper.readTree("""
        {
          "operation":"READ_BASELINE_OPTIONS",
          "baseline":null,
          "phaseRef":null,
          "phaseRefs":[],
          "slugs":[],
          "difficulty":null,
          "keyword":null,
          "cursor":null,
          "limit":null
        }
        """), context(42L, 101L));

    assertThat(options.path("status").asText()).isEqualTo("OK");
    assertThat(baselineAvailable(options, LearningPlanRevisionToolContracts.BASELINE_CURRENT_REVISION)).isTrue();
    assertThat(baselineAvailable(options, LearningPlanRevisionToolContracts.BASELINE_ORIGINAL_DRAFT)).isTrue();
    assertThat(baselineAvailable(options, LearningPlanRevisionToolContracts.BASELINE_PREVIOUS_REVISION)).isTrue();

    JsonNode originalPage = tool.execute(objectMapper.readTree("""
        {
          "operation":"READ_PHASE",
          "baseline":"ORIGINAL_DRAFT",
          "phaseRef":"phase-1",
          "phaseRefs":[],
          "slugs":[],
          "difficulty":null,
          "keyword":null,
          "cursor":null,
          "limit":2
        }
        """), context(42L, 101L));
    assertThat(originalPage.path("baseline").asText()).isEqualTo("ORIGINAL_DRAFT");
    assertThat(originalPage.path("phase").path("problemCount").asInt()).isEqualTo(18);

    ObjectNode crossBaselineRequest = (ObjectNode) objectMapper.readTree("""
        {
          "operation":"READ_PHASE",
          "baseline":"CURRENT_REVISION",
          "phaseRef":"phase-1",
          "phaseRefs":[],
          "slugs":[],
          "difficulty":null,
          "keyword":null,
          "cursor":null,
          "limit":2
        }
        """);
    crossBaselineRequest.put("cursor", originalPage.path("nextCursor").asText());

    JsonNode rejected = tool.execute(crossBaselineRequest, context(42L, 101L));

    assertThat(rejected.path("failureCode").asText()).isEqualTo("INVALID_ARGUMENTS");
  }

  @Test
  void compileToolRestoresCanonicalFieldsAndKeepsAllTemplateProblems() throws Exception {
    TestProposalRepository repository = repository(plan(149, true));
    LearningPlanRevisionCanonicalRestorer restorer = new LearningPlanRevisionCanonicalRestorer(
        catalog,
        new LearningPlanLoadService(Clock.fixed(NOW, ZoneOffset.UTC)),
        new LearningPlanDraftValidator());
    CompileLearningPlanRevisionAgentTool tool = new CompileLearningPlanRevisionAgentTool(
        repository,
        restorer,
        Clock.fixed(NOW, ZoneOffset.UTC));

    JsonNode result = tool.execute(objectMapper.readTree("""
        {
          "briefPatch":{"objective":"掌握 149 题模板中的核心模式"},
          "planPatch":{
            "title":"修订后的完整模板计划",
            "phaseChanges":[{
              "operation":"UPDATE",
              "phaseRef":"phase-1",
              "problemChanges":[{
                "operation":"REPLACE",
                "slug":"problem-1",
                "replacementSlug":"replacement-problem",
                "replacementReason":"补充新的核心模式。"
              }]
            }]
          }
        }
        """), context(42L, 101L));

    assertThat(result.path("status").asText()).isEqualTo(LearningPlanRevisionToolContracts.STATUS_PASS);
    assertThat(result.path("artifactRef").asText()).isEqualTo("draft-revision:101:compiled");
    LearningPlanDraftRevision compiled = repository.revision;
    assertThat(compiled.status()).isEqualTo(LearningPlanProposalRevisionStatus.GENERATING);
    assertThat(compiled.baseBrief()).isEqualTo(brief());
    assertThat(compiled.basePlan().title()).isEqualTo("基线计划");
    assertThat(compiled.proposedBrief().objective()).isEqualTo("掌握 149 题模板中的核心模式");
    assertThat(compiled.proposedBrief().contentLocale()).isEqualTo(LearningPlanContentLocale.EN_US);
    assertThat(compiled.proposedBrief().personalizationEnabled()).isTrue();
    assertThat(compiled.proposedPlan().phases()).hasSize(1);
    assertThat(compiled.proposedPlan().phases().get(0).problems()).hasSize(149);
    assertThat(compiled.proposedPlan().phases().get(0).phaseIndex()).isEqualTo(1);
    assertThat(compiled.proposedPlan().phases().get(0).durationWeeks()).isEqualTo(4);
    LearningPlanProblemDraft replacement = compiled.proposedPlan().phases().get(0).problems().get(0);
    assertThat(replacement)
        .extracting(
            LearningPlanProblemDraft::slug,
            LearningPlanProblemDraft::frontendId,
            LearningPlanProblemDraft::title,
            LearningPlanProblemDraft::titleCn,
            LearningPlanProblemDraft::difficulty,
            LearningPlanProblemDraft::tags,
            LearningPlanProblemDraft::reason,
            LearningPlanProblemDraft::sortOrder)
        .containsExactly(
            "replacement-problem",
            10_000,
            "Replacement Problem",
            "替换题",
            "HARD",
            List.of("Graph"),
            "补充新的核心模式。",
            1);
    Map<?, ?> template = (Map<?, ?>) compiled.proposedPlan().metadata().get(LearningPlanDraftMetadataKeys.TEMPLATE);
    assertThat(template.containsKey(LearningPlanDraftMetadataKeys.MATCHED_PROBLEM_COUNT)).isFalse();
    assertThat(compiled.proposedPlan().metadata())
        .containsKey(LearningPlanDraftMetadataKeys.LOAD_SUMMARY)
        .containsEntry(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "en-US")
        .containsEntry(LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, true);
  }

  @Test
  void compileToolRestoresEighteenProblemTemplateFromFourProblemCurrentDraft() throws Exception {
    TestProposalRepository repository = repository(
        plan(4, true),
        plan(18, true),
        plan(17, true),
        3);
    CompileLearningPlanRevisionAgentTool tool = compileTool(repository);

    JsonNode result = tool.execute(restoreRequest("ORIGINAL_DRAFT"), context(42L, 101L));

    assertThat(result.path("status").asText()).isEqualTo(LearningPlanRevisionToolContracts.STATUS_PASS);
    assertThat(result.path("baseline").asText()).isEqualTo("ORIGINAL_DRAFT");
    assertThat(result.path("changed").asBoolean()).isTrue();
    assertThat(result.path("changeSummary").path("problemCountBefore").asInt()).isEqualTo(4);
    assertThat(result.path("changeSummary").path("problemCountAfter").asInt()).isEqualTo(18);
    assertThat(repository.revision.proposedPlan().phases().get(0).problems()).hasSize(18);
    assertThat(repository.revision.proposedPlan().metadata())
        .containsEntry(LearningPlanDraftMetadataKeys.DRAFT_SOURCE, LearningPlanDraftMetadataKeys.DRAFT_SOURCE_TEMPLATE);
  }

  @Test
  void compileToolRestoresAiCreatedOriginalDraftThroughTheSameBaseline() throws Exception {
    TestProposalRepository repository = repository(
        aiPlan(2),
        aiPlan(5),
        aiPlan(4),
        3);
    CompileLearningPlanRevisionAgentTool tool = compileTool(repository);

    JsonNode result = tool.execute(restoreRequest("ORIGINAL_DRAFT"), context(42L, 101L));

    assertThat(result.path("status").asText()).isEqualTo(LearningPlanRevisionToolContracts.STATUS_PASS);
    assertThat(result.path("baseline").asText()).isEqualTo("ORIGINAL_DRAFT");
    assertThat(result.path("changeSummary").path("problemCountBefore").asInt()).isEqualTo(2);
    assertThat(result.path("changeSummary").path("problemCountAfter").asInt()).isEqualTo(5);
    assertThat(repository.revision.proposedPlan().phases())
        .flatExtracting(LearningPlanPhaseDraft::problems)
        .hasSize(5);
    assertThat(repository.revision.proposedPlan().metadata())
        .doesNotContainKey(LearningPlanDraftMetadataKeys.TEMPLATE);
  }

  @Test
  void compileToolUsesPreviousRevisionBaselineToUndoLastRevision() throws Exception {
    TestProposalRepository repository = repository(
        aiPlan(4),
        aiPlan(5),
        aiPlan(5),
        3);
    CompileLearningPlanRevisionAgentTool tool = compileTool(repository);

    JsonNode result = tool.execute(restoreRequest("PREVIOUS_REVISION"), context(42L, 101L));

    assertThat(result.path("status").asText()).isEqualTo(LearningPlanRevisionToolContracts.STATUS_PASS);
    assertThat(result.path("baseline").asText()).isEqualTo("PREVIOUS_REVISION");
    assertThat(result.path("changeSummary").path("problemCountBefore").asInt()).isEqualTo(4);
    assertThat(result.path("changeSummary").path("problemCountAfter").asInt()).isEqualTo(5);
  }

  private CompileLearningPlanRevisionAgentTool compileTool(TestProposalRepository repository) {
    return new CompileLearningPlanRevisionAgentTool(
        repository,
        new LearningPlanRevisionCanonicalRestorer(
            catalog,
            new LearningPlanLoadService(Clock.fixed(NOW, ZoneOffset.UTC)),
            new LearningPlanDraftValidator()),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private JsonNode restoreRequest(String baseline) throws Exception {
    ObjectNode request = (ObjectNode) objectMapper.readTree("""
        {
          "baseline":null,
          "briefPatch":{},
          "planPatch":{
            "title":null,
            "summary":null,
            "phaseChanges":[]
          }
        }
        """);
    request.put("baseline", baseline);
    return request;
  }

  private boolean baselineAvailable(JsonNode response, String baseline) {
    for (JsonNode option : response.path("baselines")) {
      if (baseline.equals(option.path("baseline").asText())) {
        return option.path("available").asBoolean();
      }
    }
    return false;
  }

  private List<String> fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private TestProposalRepository repository(LearningPlanDraftPlan basePlan) {
    return repository(basePlan, basePlan, null, 1);
  }

  private TestProposalRepository repository(
      LearningPlanDraftPlan basePlan,
      LearningPlanDraftPlan originPlan,
      LearningPlanDraftPlan previousPlan,
      int revisionNo
  ) {
    LearningPlanDraftRevision revision = new LearningPlanDraftRevision(
        101L,
        10L,
        20L,
        42L,
        revisionNo,
        LearningPlanProposalRevisionStatus.GENERATING,
        "修订计划",
        brief(),
        basePlan,
        null,
        null,
        null,
        null,
        NOW,
        NOW);
    LearningPlanRevisionBaseSnapshot origin = originPlan == null
        ? null
        : new LearningPlanRevisionBaseSnapshot(brief(), originPlan);
    LearningPlanRevisionBaseSnapshot previous = previousPlan == null
        ? null
        : new LearningPlanRevisionBaseSnapshot(brief(), previousPlan);
    return new TestProposalRepository(revision, origin, previous);
  }

  private AgentExecutionContext context(long userId, long revisionId) {
    return new AgentExecutionContext("run-1", 1, Map.of(
        AgentRuntimeMetadataKeys.USER_ID, userId,
        LearningPlanRevisionToolContracts.METADATA_REVISION_ID, revisionId,
        LearningPlanRevisionToolContracts.METADATA_SCENARIO, LearningPlanRevisionToolContracts.SCENARIO), false);
  }

  private LearningPlanBrief brief() {
    return new LearningPlanBrief(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备算法面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "Java",
        new LearningPlanDifficultyDistribution(30, 60, 10),
        List.of("Array", "Graph"),
        "保留核心题",
        true,
        LearningPlanContentLocale.EN_US);
  }

  private LearningPlanDraftPlan plan(int problemCount, boolean template) {
    List<LearningPlanProblemDraft> problems = new ArrayList<>();
    for (int index = 1; index <= problemCount; index++) {
      problems.add(new LearningPlanProblemDraft(
          "problem-" + index,
          index,
          "Problem " + index,
          "题目 " + index,
          index % 3 == 0 ? "HARD" : index % 2 == 0 ? "MEDIUM" : "EASY",
          List.of("Stale Tag"),
          "基线推荐理由 " + index,
          index));
    }
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "en-US");
    metadata.put(LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, true);
    if (template) {
      metadata.put(LearningPlanDraftMetadataKeys.DRAFT_SOURCE, LearningPlanDraftMetadataKeys.DRAFT_SOURCE_TEMPLATE);
      metadata.put(LearningPlanDraftMetadataKeys.TEMPLATE, Map.of(
          LearningPlanDraftMetadataKeys.TEMPLATE_ID, "demo-149",
          LearningPlanDraftMetadataKeys.MATCHED_PROBLEM_COUNT, problemCount));
    }
    return new LearningPlanDraftPlan(
        "基线计划",
        "覆盖完整模板题单。",
        brief().intent(),
        brief().objective(),
        brief().durationWeeks(),
        brief().level(),
        brief().weeklyHours(),
        brief().programmingLanguage(),
        brief().difficultyDistribution(),
        brief().topicPreferences(),
        brief().additionalConstraints(),
        List.of(new LearningPlanPhaseDraft(1, "完整题单", 4, "Mixed", problems)),
        metadata);
  }

  private LearningPlanDraftPlan aiPlan(int problemCount) {
    LearningPlanDraftPlan source = plan(problemCount, false);
    List<LearningPlanProblemDraft> problems = source.phases().get(0).problems();
    int split = Math.max(1, (problemCount + 1) / 2);
    return new LearningPlanDraftPlan(
        source.title(),
        source.summary(),
        source.intent(),
        source.objective(),
        source.durationWeeks(),
        source.level(),
        source.weeklyHours(),
        source.programmingLanguage(),
        source.difficultyDistribution(),
        source.topicPreferences(),
        source.additionalConstraints(),
        List.of(
            new LearningPlanPhaseDraft(1, "基础阶段", 2, "Foundation", problems.subList(0, split)),
            new LearningPlanPhaseDraft(2, "进阶阶段", 2, "Advanced", problems.subList(split, problemCount))),
        source.metadata());
  }

  private static final class DemoProblemCatalog implements LearningPlanProblemCatalog {

    @Override
    public List<LearningPlanProblemCandidate> searchProblems(LearningPlanProblemSearch search) {
      return List.of();
    }

    @Override
    public Optional<LearningPlanProblemCandidate> findBySlug(String slug) {
      if ("replacement-problem".equals(slug)) {
        return Optional.of(new LearningPlanProblemCandidate(
            slug, 10_000, "Replacement Problem", "替换题", "HARD", List.of("Graph")));
      }
      if (slug != null && slug.startsWith("problem-")) {
        int index = Integer.parseInt(slug.substring("problem-".length()));
        return Optional.of(new LearningPlanProblemCandidate(
            slug,
            index,
            "Canonical Problem " + index,
            "规范题目 " + index,
            index % 3 == 0 ? "HARD" : index % 2 == 0 ? "MEDIUM" : "EASY",
            List.of("Canonical Tag")));
      }
      return Optional.empty();
    }
  }

  private static final class TestProposalRepository implements LearningPlanProposalRepository {

    private LearningPlanDraftRevision revision;
    private final LearningPlanRevisionBaseSnapshot origin;
    private final LearningPlanRevisionBaseSnapshot previous;

    private TestProposalRepository(
        LearningPlanDraftRevision revision,
        LearningPlanRevisionBaseSnapshot origin,
        LearningPlanRevisionBaseSnapshot previous
    ) {
      this.revision = revision;
      this.origin = origin;
      this.previous = previous;
    }

    @Override
    public LearningPlanDraftRevision saveDraftRevision(LearningPlanDraftRevision revision) {
      this.revision = revision;
      return revision;
    }

    @Override
    public Optional<LearningPlanDraftRevision> findDraftRevisionForUser(long revisionId, long userId) {
      return Optional.ofNullable(revision)
          .filter(value -> value.id() == revisionId && value.userId() == userId);
    }

    @Override
    public Optional<LearningPlanRevisionBaseSnapshot> findDraftOriginForUser(long draftId, long userId) {
      return Optional.ofNullable(origin)
          .filter(value -> revision.draftId() == draftId && revision.userId() == userId);
    }

    @Override
    public Optional<LearningPlanRevisionBaseSnapshot> findPreviousDraftRevisionBaseForUser(
        long proposalGroupId,
        int beforeRevisionNo,
        long userId
    ) {
      return Optional.ofNullable(previous)
          .filter(value -> revision.proposalGroupId() == proposalGroupId
              && revision.revisionNo() == beforeRevisionNo
              && revision.userId() == userId);
    }

    @Override
    public LearningPlanProposalGroup saveGroup(LearningPlanProposalGroup group) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<LearningPlanProposalGroup> findGroupForUser(long groupId, long userId) {
      return Optional.empty();
    }

    @Override
    public Optional<LearningPlanProposalGroup> findLatestActiveGroup(
        long userId,
        LearningPlanProposalType proposalType,
        LearningPlanProposalTargetType targetType,
        long targetId
    ) {
      return Optional.empty();
    }

    @Override
    public LearningPlanExtensionRevision saveExtensionRevision(LearningPlanExtensionRevision revision) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<LearningPlanExtensionRevision> findExtensionRevisionForUser(long revisionId, long userId) {
      return Optional.empty();
    }

    @Override
    public Optional<LearningPlanExtensionRevision> findLatestReadyExtensionRevision(long proposalGroupId) {
      return Optional.empty();
    }

    @Override
    public int nextRevisionNo(long proposalGroupId) {
      return 2;
    }

    @Override
    public List<Long> markReadyDraftRevisionsSuperseded(long proposalGroupId, long exceptRevisionId) {
      return List.of();
    }

    @Override
    public List<Long> markReadyExtensionRevisionsSuperseded(long proposalGroupId, long exceptRevisionId) {
      return List.of();
    }
  }
}
