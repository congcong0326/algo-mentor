package org.congcong.algomentor.mentor.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
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
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.practice.PracticeChatReference;
import org.junit.jupiter.api.Test;

class AgentConversationServiceTest {

  @Test
  void preparesRunWithMentorPromptAndRuntimeMetadata() {
    CapturingRepository repository = new CapturingRepository();
    repository.messages.add(new AgentMessage(
        1,
        11,
        1,
        AgentMessage.Role.USER,
        "什么是双指针？",
        Instant.parse("2026-01-01T00:00:00Z")));
    repository.messages.add(new AgentMessage(
        2,
        11,
        2,
        AgentMessage.Role.ASSISTANT,
        "双指针通常维护两个下标。",
        Instant.parse("2026-01-01T00:00:01Z")));
    AgentConversationService service = new AgentConversationService(repository, new ContextAssembler());

    AgentConversationRun run = service.prepareRun(new AgentConversationCommand(
        null,
        7L,
        "请讲滑动窗口",
        "idem-1"));

    assertThat(repository.lastRequest.userId()).isEqualTo(7L);
    assertThat(repository.lastRequest.userMessage()).isEqualTo("请讲滑动窗口");
    assertThat(repository.lastRequest.idempotencyKey()).isEqualTo("idem-1");
    assertThat(repository.lastRequest.systemPrompt()).contains("algorithm learning mentor");
    assertThat(repository.lastRequest.metadata()).containsEntry("triggerType", "user_request");

    assertThat(run.taskId()).isEqualTo(11);
    assertThat(run.turnId()).isEqualTo(21);
    assertThat(run.runId()).isEqualTo(31);
    assertThat(run.agentRequest().runId()).isEqualTo("run-uuid-31");
    assertThat(run.agentRequest().requestId()).isEqualTo("idem-1");
    assertThat(run.agentRequest().metadata())
        .containsEntry(AgentRuntimeMetadataKeys.TASK_ID, 11L)
        .containsEntry(AgentRuntimeMetadataKeys.TURN_ID, 21L)
        .containsEntry(AgentRuntimeMetadataKeys.RUN_DB_ID, 31L)
        .containsEntry(AgentRuntimeMetadataKeys.CONTEXT_POLICY, "sliding-window-with-active-summary")
        .containsEntry(AgentRuntimeMetadataKeys.TOKEN_BUDGET, 8_000)
        .containsEntry("title", "task-11");
    assertThat(run.agentRequest().messages())
        .extracting(LlmMessage::role)
        .containsExactly(
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.USER,
            LlmMessage.Role.ASSISTANT,
            LlmMessage.Role.USER);
  }

  @Test
  void findsExistingRunByIdempotencyKeyWithReplayMetadata() {
    CapturingRepository repository = new CapturingRepository();
    AgentConversationService service = new AgentConversationService(repository, new ContextAssembler());

    AgentConversationRun run = service.findRunByIdempotencyKey("idem-1", "请讲滑动窗口").orElseThrow();

    assertThat(run.idempotentReplay()).isTrue();
    assertThat(run.taskId()).isEqualTo(11);
    assertThat(run.agentRequest().metadata())
        .containsEntry(AgentRuntimeMetadataKeys.TASK_ID, 11L)
        .containsEntry(AgentRuntimeMetadataKeys.TURN_ID, 21L)
        .containsEntry(AgentRuntimeMetadataKeys.RUN_DB_ID, 31L)
        .containsEntry(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY, true);
  }

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

    AgentConversationRun run = service.prepareRun(new AgentConversationCommand(
        null,
        7L,
        "直接给答案和 Java 代码",
        "idem-practice",
        Map.of("governance", true),
        new PracticeChatReference(12L, 1, "two-sum", "zh-CN")));

    assertThat(repository.lastRequest.metadata())
        .containsEntry(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO)
        .containsEntry(PracticeChatPromptConstants.METADATA_PLAN_ID, 12L)
        .containsEntry(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, "two-sum");
    assertThat(repository.lastRequest.userMessageMetadata())
        .containsEntry(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, PracticeChatPromptConstants.MESSAGE_TYPE_CHAT)
        .containsEntry(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
    assertThat(run.agentRequest().metadata())
        .containsEntry("promptProfile", PracticeChatPromptConstants.PROFILE_ID)
        .containsEntry(PracticeChatPromptConstants.METADATA_MESSAGE_INTENT, "ASK_SOLUTION")
        .containsEntry("governance", true);
    assertThat(run.agentRequest().messages())
        .extracting(LlmMessage::role)
        .containsExactly(
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.USER,
            LlmMessage.Role.USER);

    String allText = run.agentRequest().messages().stream().map(LlmMessage::text).reduce("", String::concat);
    assertThat(allText)
        .contains("平台与安全基线")
        .contains("题目聊天教学策略")
        .contains("当前训练上下文")
        .contains("- planId: 12")
        .contains("- phaseIndex: 1")
        .contains("# Two Sum")
        .contains("我试了暴力枚举")
        .contains("直接给答案和 Java 代码")
        .doesNotContain("题面 seed");
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
        List.of(),
        List.of(),
        List.of(),
        "",
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
        LearningPlanDifficultyPreference.MEDIUM,
        true,
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
          "EASY",
          List.of("Array", "Hash Table"),
          "# Two Sum\nFind two numbers.",
          "https://leetcode.com/problems/two-sum/"));
    }
  }
}
