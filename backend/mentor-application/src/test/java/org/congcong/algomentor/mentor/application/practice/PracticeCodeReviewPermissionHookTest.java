package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionBehavior;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionCheck;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionPlan;
import org.congcong.algomentor.agent.core.permission.ToolNamePermissionHook;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewPermissionHookTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final long USER_ID = 7L;
  private static final long SESSION_ID = 50L;
  private static final long PLAN_ID = 12L;
  private static final int PHASE_INDEX = 1;
  private static final long RUN_DB_ID = 501L;
  private static final long USER_MESSAGE_ID = 701L;
  private static final String PROBLEM_SLUG = "climbing-stairs";
  private static final String USER_MESSAGE_CONTENT =
      "class Solution { public int climbStairs(int n) { return n; } }";

  @Test
  void nonReviewToolPassesThrough() {
    PracticeCodeReviewPermissionHook hook = hook(session(), turnMessages(USER_MESSAGE_CONTENT));

    AgentToolPermissionDecisionPlan plan = hook.evaluate(check("calculator", metadata(), toolArguments()));

    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.PASSTHROUGH);
    assertThat(plan.preview()).isEmpty();
    assertThat(plan.displayName()).isNull();
    assertThat(plan.reason()).isNull();
  }

  @Test
  void reviewToolAsksWithTrustedPreview() {
    PracticeCodeReviewPermissionHook hook = hook(session(), turnMessages(USER_MESSAGE_CONTENT));

    AgentToolPermissionDecisionPlan plan = hook.evaluate(check(
        PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW,
        metadata(),
        toolArguments()));

    assertThat(hook.order()).isLessThan(ToolNamePermissionHook.DEFAULT_ORDER);
    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.ASK);
    assertThat(plan.displayName()).isEqualTo(PracticeCodeReviewPermissionHook.DISPLAY_NAME);
    assertThat(plan.reason()).isEqualTo(PracticeCodeReviewPermissionHook.REASON);
    assertThat(plan.policySource()).isEqualTo(PracticeCodeReviewPermissionHook.POLICY_SOURCE);
    assertThat(plan.metadata()).isEmpty();
    assertThat(plan.preview())
        .containsEntry(PracticeCodeReviewAgentToolNames.PREVIEW_PROBLEM_SLUG, PROBLEM_SLUG)
        .containsEntry(PracticeCodeReviewAgentToolNames.PREVIEW_PROBLEM_TITLE, PROBLEM_SLUG)
        .containsEntry(PracticeCodeReviewAgentToolNames.PREVIEW_LANGUAGE_HINT, "java")
        .containsEntry(PracticeCodeReviewAgentToolNames.PREVIEW_CODE_LENGTH, USER_MESSAGE_CONTENT.length())
        .containsEntry(PracticeCodeReviewAgentToolNames.PREVIEW_CODE_PREVIEW, USER_MESSAGE_CONTENT)
        .containsEntry(PracticeCodeReviewAgentToolNames.PREVIEW_CONTEXT_AVAILABLE, true);
    assertThat(plan.preview().get(PracticeCodeReviewAgentToolNames.PREVIEW_EFFECTS))
        .asList()
        .anySatisfy(value -> assertThat(value).asString().contains("正式 Review"));
    assertThat(plan.preview().toString())
        .doesNotContain("model-controlled-slug")
        .doesNotContain("malicious code from model arguments");
  }

  @Test
  void codePreviewIsTruncatedAndCodeLengthUsesFullMessageLength() {
    String content = """
        class Solution {}
        Authorization: Bearer secret-token
        Cookie: SESSION=secret-cookie
        """
        + "x".repeat(620);
    PracticeCodeReviewPermissionHook hook = hook(session(), turnMessages(content));

    AgentToolPermissionDecisionPlan plan = hook.evaluate(check(
        PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW,
        metadata(),
        toolArguments()));

    String codePreview = (String) plan.preview().get(PracticeCodeReviewAgentToolNames.PREVIEW_CODE_PREVIEW);
    assertThat(plan.preview())
        .containsEntry(PracticeCodeReviewAgentToolNames.PREVIEW_CODE_LENGTH, content.length());
    assertThat(codePreview).hasSizeLessThanOrEqualTo(500);
    assertThat(codePreview).hasSize(500);
    assertThat(codePreview)
        .doesNotContain("Authorization")
        .doesNotContain("Cookie")
        .doesNotContain("secret-token")
        .doesNotContain("secret-cookie");
  }

  @Test
  void reviewToolStillAsksWithFallbackPreviewWhenContextIsMissing() {
    AgentToolPermissionDecisionPlan missingMetadataPlan = hook(session(), turnMessages(USER_MESSAGE_CONTENT))
        .evaluate(check(
            PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW,
            Map.of(AgentRuntimeMetadataKeys.USER_ID, USER_ID),
            toolArguments()));
    AgentToolPermissionDecisionPlan missingSessionPlan = hook(null, turnMessages(USER_MESSAGE_CONTENT))
        .evaluate(check(
            PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW,
            metadata(),
            toolArguments()));
    AgentToolPermissionDecisionPlan missingMessagePlan = hook(session(), null)
        .evaluate(check(
            PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW,
            metadata(),
            toolArguments()));

    assertFallbackAsk(missingMetadataPlan);
    assertFallbackAsk(missingSessionPlan);
    assertFallbackAsk(missingMessagePlan);
  }

  @Test
  void hookDoesNotDependOnPracticeCodeReviewService() {
    assertThat(Arrays.stream(PracticeCodeReviewPermissionHook.class.getDeclaredConstructors())
        .map(Constructor::getParameterTypes)
        .flatMap(Arrays::stream)
        .anyMatch(PracticeCodeReviewService.class::equals))
        .isFalse();
    assertThat(Arrays.stream(PracticeCodeReviewPermissionHook.class.getDeclaredFields())
        .map(Field::getType)
        .anyMatch(PracticeCodeReviewService.class::equals))
        .isFalse();
  }

  private void assertFallbackAsk(AgentToolPermissionDecisionPlan plan) {
    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.ASK);
    assertThat(plan.displayName()).isEqualTo(PracticeCodeReviewPermissionHook.DISPLAY_NAME);
    assertThat(plan.reason()).isEqualTo(PracticeCodeReviewPermissionHook.REASON);
    assertThat(plan.policySource()).isEqualTo(PracticeCodeReviewPermissionHook.POLICY_SOURCE);
    assertThat(plan.preview())
        .containsEntry(PracticeCodeReviewAgentToolNames.PREVIEW_CONTEXT_AVAILABLE, false)
        .containsKey(PracticeCodeReviewAgentToolNames.PREVIEW_EFFECTS);
    assertThat(plan.preview()).doesNotContainKeys(
        PracticeCodeReviewAgentToolNames.PREVIEW_CODE_PREVIEW,
        PracticeCodeReviewAgentToolNames.PREVIEW_CODE_LENGTH);
  }

  private PracticeCodeReviewPermissionHook hook(PracticeSession session, AgentTurnMessages turnMessages) {
    return new PracticeCodeReviewPermissionHook(
        new StubPracticeSessionRepository(session),
        new StubTurnMessageLookupRepository(turnMessages));
  }

  private AgentToolPermissionCheck check(String toolName, Map<String, Object> metadata, JsonNode arguments) {
    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("hello")),
        metadata);
    AgentLoopContext context = new AgentLoopContext("run-1", request, 4, request.metadata());
    return new AgentToolPermissionCheck(
        context,
        1,
        new LlmToolCall("call-1", toolName, arguments),
        tool(toolName),
        request.metadata());
  }

  private JsonNode toolArguments() {
    return OBJECT_MAPPER.createObjectNode()
        .put(PracticeCodeReviewAgentToolNames.PREVIEW_PROBLEM_SLUG, "model-controlled-slug")
        .put("code", "malicious code from model arguments");
  }

  private AgentTool tool(String toolName) {
    return new AgentTool() {
      @Override
      public LlmToolSpec spec() {
        return new LlmToolSpec(
            toolName,
            "Test tool " + toolName,
            OBJECT_MAPPER.createObjectNode().put("type", "object"),
            true);
      }

      @Override
      public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
        return OBJECT_MAPPER.createObjectNode().put("ok", true);
      }
    };
  }

  private Map<String, Object> metadata() {
    return Map.of(
        AgentRuntimeMetadataKeys.USER_ID, USER_ID,
        PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, SESSION_ID,
        AgentRuntimeMetadataKeys.RUN_DB_ID, RUN_DB_ID);
  }

  private PracticeSession session() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    return new PracticeSession(
        SESSION_ID,
        USER_ID,
        PLAN_ID,
        PHASE_INDEX,
        PROBLEM_SLUG,
        PracticeSessionStatus.ACTIVE,
        100L,
        200L,
        PracticeProgressStatus.IN_PROGRESS,
        null,
        now,
        now,
        "zh-CN");
  }

  private AgentTurnMessages turnMessages(String content) {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    AgentMessage userMessage = new AgentMessage(
        USER_MESSAGE_ID,
        100L,
        1L,
        AgentMessage.Role.USER,
        content,
        now);
    return new AgentTurnMessages(RUN_DB_ID, 200L, userMessage, null);
  }

  private static final class StubPracticeSessionRepository implements PracticeSessionRepository {
    private final PracticeSession session;

    private StubPracticeSessionRepository(PracticeSession session) {
      this.session = session;
    }

    @Override
    public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex, String problemSlug) {
      throw new UnsupportedOperationException("upsert progress not used");
    }

    @Override
    public PracticeSession upsertAndLockSession(
        long userId,
        long planId,
        int phaseIndex,
        String problemSlug,
        String locale
    ) {
      throw new UnsupportedOperationException("upsert session not used");
    }

    @Override
    public Optional<PracticeSession> findSessionForUser(long sessionId, long userId) {
      return Optional.ofNullable(session)
          .filter(value -> value.id() == sessionId && value.userId() == userId);
    }

    @Override
    public PracticeSession attachAgentTask(long sessionId, long agentTaskId) {
      throw new UnsupportedOperationException("attach task not used");
    }

    @Override
    public PracticeSession attachProblemStatementMessage(long sessionId, long messageId) {
      throw new UnsupportedOperationException("attach seed not used");
    }

    @Override
    public PracticeProgress updateProgressStatus(long sessionId, long userId, PracticeProgressStatus status) {
      throw new UnsupportedOperationException("update progress not used");
    }

    @Override
    public void touchLastMessageAt(long sessionId) {
      throw new UnsupportedOperationException("touch message not used");
    }
  }

  private static final class StubTurnMessageLookupRepository implements AgentTurnMessageLookupRepository {
    private final AgentTurnMessages turnMessages;

    private StubTurnMessageLookupRepository(AgentTurnMessages turnMessages) {
      this.turnMessages = turnMessages;
    }

    @Override
    public Optional<AgentTurnMessages> findByRunId(long runId) {
      return Optional.ofNullable(turnMessages)
          .filter(value -> value.runId() == runId);
    }
  }
}
