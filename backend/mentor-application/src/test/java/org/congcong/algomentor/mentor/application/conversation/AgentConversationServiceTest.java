package org.congcong.algomentor.mentor.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssemblyPolicy;
import org.congcong.algomentor.agent.core.prompt.DefaultPromptAssembler;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemCatalog;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemDetail;
import org.congcong.algomentor.mentor.application.practice.PracticeChatAgentInput;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.practice.PracticeChatReference;
import org.congcong.algomentor.mentor.application.practice.PracticeCoachStyle;
import org.congcong.algomentor.mentor.application.practice.PracticeResponseLanguage;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptProfileResolver;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptSectionProvider;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.repository.LearnerMemoryClaimRepository;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimQueryService;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimSnapshotFactory;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryDirectHitSelector;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryDocumentRevision;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallBootstrapBuilder;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallContracts;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallPromptSectionProvider;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallService;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallSnapshot;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemorySectionCatalog;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryRunScopeRegistry;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryAgentToolContracts;
import org.congcong.algomentor.mentor.application.profile.tool.PracticeChatReviewTrajectoryScopeService;
import org.junit.jupiter.api.Test;

class AgentConversationServiceTest {

  @Test
  void preparesPracticeChatRunWithPromptAssemblyAndFiltersProblemStatementHistory() {
    CapturingRepository repository = new CapturingRepository();
    repository.messages.add(new AgentMessage(
        1,
        11,
        1,
        AgentMessage.Role.ASSISTANT,
        "题面 seed",
        Instant.parse("2026-01-01T00:00:00Z"),
        Map.of(
            PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY,
            PracticeChatPromptConstants.MESSAGE_TYPE_PROBLEM_STATEMENT)));
    repository.messages.add(new AgentMessage(
        2,
        11,
        2,
        AgentMessage.Role.USER,
        "我试了暴力枚举",
        Instant.parse("2026-01-01T00:00:01Z"),
        Map.of(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, PracticeChatPromptConstants.MESSAGE_TYPE_CHAT)));
    AgentConversationService service = new AgentConversationService(
        repository,
        new ContextAssembler(),
        new InMemoryPlanRepository(plan()),
        new FakePracticeProblemCatalog());

    AgentConversationRun run = service.preparePracticeRun(practiceInput(
        "直接给答案和 Java 代码", "idem-practice", PracticeCoachStyle.GUIDED, PracticeResponseLanguage.ZH_CN));

    assertThat(repository.lastRequest.metadata())
        .containsEntry(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO)
        .containsEntry(PracticeChatPromptConstants.METADATA_PLAN_ID, 12L)
        .containsEntry(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, "two-sum");
    assertThat(repository.lastRequest.userMessageMetadata())
        .containsEntry(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, PracticeChatPromptConstants.MESSAGE_TYPE_CHAT)
        .containsEntry(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
    assertThat(run.agentRequest().metadata())
        .containsEntry(AgentRuntimeMetadataKeys.TASK_ID, 11L)
        .containsEntry(AgentRuntimeMetadataKeys.TURN_ID, 21L)
        .containsEntry(AgentRuntimeMetadataKeys.RUN_DB_ID, 31L)
        .containsEntry(AgentRuntimeMetadataKeys.USER_ID, 7L)
        .containsEntry("promptProfile", PracticeChatPromptConstants.PROFILE_ID)
        .containsEntry(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO)
        .containsEntry(PracticeChatPromptConstants.METADATA_PLAN_ID, 12L)
        .containsEntry(PracticeChatPromptConstants.METADATA_PHASE_INDEX, 1)
        .containsEntry(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, "two-sum")
        .containsEntry(PracticeChatPromptConstants.METADATA_LOCALE, "zh-CN");
    assertThat(run.agentRequest().messages())
        .extracting(LlmMessage::role)
        .containsExactly(
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.USER,
            LlmMessage.Role.USER);

    String allText = run.agentRequest().messages().stream().map(LlmMessage::text).reduce("", String::concat);
    assertThat(allText)
        .contains("平台与安全基线")
        .contains("引导型教练")
        .contains("分层提示协议")
        .contains("面向学习者的回复语言：简体中文")
        .contains("题目聊天教学策略")
        .contains("当前训练上下文")
        .contains("- planId: 12")
        .contains("- phaseIndex: 1")
        .contains("# Two Sum")
        .contains("我试了暴力枚举")
        .contains("直接给答案和 Java 代码")
        .doesNotContain("题面 seed");
  }

  @Test
  void preparesPracticeChatRunWithConfiguredCoachStyleAndResponseLanguage() {
    CapturingRepository repository = new CapturingRepository();
    AgentConversationService service = new AgentConversationService(
        repository,
        new ContextAssembler(),
        new InMemoryPlanRepository(plan()),
        new FakePracticeProblemCatalog());

    AgentConversationRun run = service.preparePracticeRun(practiceInput(
        "请模拟面试追问", "idem-practice", PracticeCoachStyle.DIRECT, PracticeResponseLanguage.EN_US));

    assertThat(run.agentRequest().metadata())
        .containsEntry(PracticeChatPromptConstants.METADATA_COACH_STYLE, "DIRECT")
        .containsEntry(PracticeChatPromptConstants.METADATA_RESPONSE_LANGUAGE, "EN_US");
    String allText = run.agentRequest().messages().stream().map(LlmMessage::text).reduce("", String::concat);
    assertThat(allText)
        .contains("直给型教练")
        .contains("采用简洁、直接的讲解方式")
        .contains("面向学习者的回复语言：英语");
  }

  @Test
  void opensLearnerMemorySnapshotOnceBeforeAssemblyAndKeepsClaimTextOutOfMetadata() {
    CapturingRepository repository = new CapturingRepository();
    LearnerMemoryRecallPromptSectionProvider recallProvider = new LearnerMemoryRecallPromptSectionProvider(
        new LearnerMemoryRecallBootstrapBuilder(1_000), null);
    CountingRecallService recallService = new CountingRecallService(memoryClaim("immutable-memory"));
    AgentConversationService service = new AgentConversationService(
        repository,
        new ContextAssembler(),
        ContextAssemblyPolicy.defaultPolicy(),
        new InMemoryPlanRepository(plan()),
        new FakePracticeProblemCatalog(),
        new DefaultPromptAssembler(
            new PracticeChatPromptProfileResolver(),
            List.of(new PracticeChatPromptSectionProvider(), recallProvider)),
        recallService,
        recallProvider);

    AgentConversationRun run = service.preparePracticeRun(practiceInput(
        "给我一个提示", "idem-profile-snapshot", PracticeCoachStyle.GUIDED, PracticeResponseLanguage.ZH_CN));

    assertThat(recallService.calls).isEqualTo(1);
    assertThat(run.agentRequest().messages().stream().map(LlmMessage::text).reduce("", String::concat))
        .contains("immutable-memory");
    assertThat(run.agentRequest().metadata())
        .containsEntry(LearnerMemoryRecallContracts.METADATA_CLAIM_COUNT, 1)
        .containsEntry(LearnerMemoryRecallContracts.METADATA_BOOTSTRAP_TRIMMED, false)
        .containsKey(LearnerMemoryRecallContracts.METADATA_SCOPE_REF)
        .doesNotContainValue("immutable-memory");
  }

  @Test
  void doesNotOpenLearnerMemorySnapshotWhenFindingAnIdempotentPracticeReplay() {
    CapturingRepository repository = new CapturingRepository();
    CountingRecallService recallService = new CountingRecallService(memoryClaim("must-not-open"));
    LearnerMemoryRecallPromptSectionProvider recallProvider = new LearnerMemoryRecallPromptSectionProvider(
        new LearnerMemoryRecallBootstrapBuilder(1_000), null);
    AgentConversationService service = new AgentConversationService(
        repository,
        new ContextAssembler(),
        ContextAssemblyPolicy.defaultPolicy(),
        new InMemoryPlanRepository(plan()),
        new FakePracticeProblemCatalog(),
        new DefaultPromptAssembler(
            new PracticeChatPromptProfileResolver(),
            List.of(new PracticeChatPromptSectionProvider(), recallProvider)),
        recallService,
        recallProvider);

    AgentConversationRun replay = service.findPracticeRunByIdempotencyKey(new PracticeChatAgentInput(
        7L, 8L, 11L, 12L, 1, "two-sum", "给我提示", "idem-replay", "zh-CN",
        PracticeCoachStyle.GUIDED, PracticeResponseLanguage.ZH_CN, 24)).orElseThrow();

    assertThat(replay.idempotentReplay()).isTrue();
    assertThat(recallService.calls).isZero();
    assertThat(replay.agentRequest().metadata())
        .doesNotContainKey(LearnerMemoryRecallContracts.METADATA_SCOPE_REF)
        .doesNotContainValue("must-not-open");
  }

  @Test
  void opensAndReleasesCurrentProblemReviewTrajectoryScopeWithThePracticeRun() {
    CapturingRepository repository = new CapturingRepository();
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    LearnerMemoryRecallPromptSectionProvider recallProvider = new LearnerMemoryRecallPromptSectionProvider(
        new LearnerMemoryRecallBootstrapBuilder(1_000), null);
    AgentConversationService service = new AgentConversationService(
        repository,
        new ContextAssembler(),
        ContextAssemblyPolicy.defaultPolicy(),
        new InMemoryPlanRepository(plan()),
        new FakePracticeProblemCatalog(),
        new DefaultPromptAssembler(
            new PracticeChatPromptProfileResolver(),
            List.of(new PracticeChatPromptSectionProvider(), recallProvider)),
        null,
        recallProvider,
        null,
        new PracticeChatReviewTrajectoryScopeService(registry));

    AgentConversationRun run = service.preparePracticeRun(practiceInput(
        "我这题有没有进步", "idem-review-trajectory", PracticeCoachStyle.GUIDED, PracticeResponseLanguage.ZH_CN));
    String scopeRef = run.agentRequest().metadata()
        .get(LearnerMemoryAgentToolContracts.METADATA_SCOPE_REF).toString();

    assertThat(scopeRef).isNotEqualTo("7").hasSize(43);
    assertThat(registry.reserveTrajectory(scopeRef, "other").status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.FORBIDDEN);

    run.runResource().release();
    assertThat(registry.reserveTrajectory(scopeRef, "two-sum").status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.SCOPE_UNAVAILABLE);
  }

  private static PracticeChatAgentInput practiceInput(
      String message,
      String idempotencyKey,
      PracticeCoachStyle coachStyle,
      PracticeResponseLanguage responseLanguage
  ) {
    return new PracticeChatAgentInput(
        7L,
        8L,
        11L,
        12L,
        1,
        "two-sum",
        message,
        idempotencyKey,
        "zh-CN",
        coachStyle,
        responseLanguage,
        message.length());
  }

  private static final class CapturingRepository implements AgentConversationRepository {

    private final List<AgentMessage> messages = new ArrayList<>();
    private AgentRunPreparationRequest lastRequest;

    @Override
    public PreparedAgentRun createOrReuseRun(AgentRunPreparationRequest request) {
      this.lastRequest = request;
      return new PreparedAgentRun(
          11,
          21,
          31,
          "run-uuid-31",
          request.idempotencyKey(),
          request.systemPrompt(),
          null,
          Map.of("repositoryMetadata", true));
    }

    @Override
    public Optional<PreparedAgentRun> findRunByIdempotencyKey(String idempotencyKey) {
      return Optional.of(new PreparedAgentRun(
          11,
          21,
          31,
          "run-uuid-31",
          idempotencyKey,
          "system",
          null,
          Map.of(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY, true)));
    }

    @Override
    public List<AgentMessage> recentMessages(long taskId, int messageLimit) {
      assertThat(taskId).isEqualTo(11);
      assertThat(messageLimit).isEqualTo(16);
      return messages;
    }
  }

  private static LearningPlan plan() {
    LearningPlanProblemDraft problem = new LearningPlanProblemDraft(
        "two-sum",
        1,
        "Two Sum",
        "两数之和",
        "EASY",
        List.of("Array", "Hash Table"),
        "建立哈希查找模式。",
        1);
    LearningPlanPhaseDraft phase = new LearningPlanPhaseDraft(
        1,
        "哈希表基础",
        1,
        "补数查找",
        List.of(problem));
    LearningPlanDraftPlan snapshot = new LearningPlanDraftPlan(
        "哈希表训练",
        "summary",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "4 周内准备后端面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "Java",
        new org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Hash Table"),
        "profile",
        List.of(phase),
        Map.of());
    return new LearningPlan(
        12L,
        7L,
        LearningPlanStatus.ACTIVE,
        snapshot,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  private record InMemoryPlanRepository(LearningPlan plan) implements LearningPlanRepository {

    @Override
    public LearningPlan save(LearningPlan plan) {
      return plan;
    }

    @Override
    public List<LearningPlan> findByUserId(long userId) {
      return List.of(plan);
    }

    @Override
    public LearningPlanPage findPageByUserId(long userId, int page, int pageSize) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<LearningPlan> findPlanByIdForUser(long planId, long userId) {
      return plan.id() == planId && plan.userId() == userId ? Optional.of(plan) : Optional.empty();
    }
  }

  private static final class FakePracticeProblemCatalog implements PracticeChatProblemCatalog {

    @Override
    public Optional<PracticeChatProblemDetail> findProblemBySlug(String slug, String locale) {
      return Optional.of(new PracticeChatProblemDetail(
          slug,
          1,
          "Two Sum",
          "两数之和",
          "EASY",
          List.of("Array", "Hash Table"),
          "# Two Sum\nFind two numbers.",
          "https://leetcode.com/problems/two-sum/"));
    }
  }

  private static LearnerMemoryClaimRevision memoryClaim(String content) {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    return new LearnerMemoryClaimRevision(
        1L,
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        7L,
        new LearnerMemoryClaimScope(
            LearnerMemoryClaimContract.Kind.DECLARED_FACT,
            LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS,
            null),
        1,
        LearnerMemoryClaimContract.RevisionStatus.ACTIVE,
        content,
        "a".repeat(64),
        LearnerMemoryClaimContract.Origin.USER_EXPLICIT,
        LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION,
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED,
        null,
        1L,
        null,
        now,
        null,
        now,
        now);
  }

  private static final class CountingRecallService extends LearnerMemoryRecallService {
    private final LearnerMemoryRunScopeRegistry.RecallScopeLease lease;
    private int calls;

    private CountingRecallService(LearnerMemoryClaimRevision claim) {
      super(
          new LearnerMemoryClaimQueryService(new EmptyClaimRepository(), new LearnerMemoryClaimSnapshotFactory()),
          ignored -> List.of(),
          new LearnerMemorySectionCatalog(),
          new LearnerMemoryDirectHitSelector(),
          new LearnerMemoryRunScopeRegistry());
      LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
      this.lease = registry.openRecallScope(
          7L,
          LearnerMemoryDocumentRevision.calculate("zh-CN", List.of(claim)),
          "zh-CN",
          List.of(new LearnerMemoryRecallSnapshot.SectionInput(
              "background-goals",
              "学习背景与目标",
              List.of(new LearnerMemoryRecallSnapshot.StatementInput(claim, "用户明确自述", false)))),
          List.of(claim.id()));
    }

    @Override
    public OpenedSnapshot openSnapshot(long userId, String currentUserMessage, String problemSlug, String locale) {
      calls++;
      return new OpenedSnapshot(lease.snapshot(), lease);
    }
  }

  private static final class EmptyClaimRepository implements LearnerMemoryClaimRepository {
    @Override
    public List<LearnerMemoryClaimRevision> findActiveByUser(long userId) {
      return List.of();
    }

    @Override
    public List<LearnerMemoryClaimRevision> findActiveByScopes(long userId, java.util.Collection<LearnerMemoryClaimScope> scopes) {
      return List.of();
    }

    @Override
    public List<LearnerMemoryClaimRevision> findActiveByRevisionIds(
        long userId, java.util.Collection<Long> revisionIds) {
      return List.of();
    }

    @Override
    public List<LearnerMemoryClaimRevision> findActiveByUserForUpdate(long userId) {
      return List.of();
    }

    @Override
    public Optional<LearnerMemoryClaimRevision> findCurrent(long userId, UUID claimKey) {
      return Optional.empty();
    }

    @Override
    public List<LearnerMemoryClaimRevision> findHistory(long userId, UUID claimKey) {
      return List.of();
    }

    @Override
    public long countActiveByUser(long userId) {
      return 0;
    }

    @Override
    public long countActiveByScope(long userId, LearnerMemoryClaimScope scope) {
      return 0;
    }

    @Override
    public void lockUser(long userId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public LearnerMemoryClaimRevision insert(
        org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevisionDraft draft) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void markCurrentSuperseded(long revisionId, Instant validTo) {
      throw new UnsupportedOperationException();
    }
  }
}
