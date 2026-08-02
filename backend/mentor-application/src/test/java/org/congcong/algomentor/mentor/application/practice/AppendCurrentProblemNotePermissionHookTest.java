package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionBehavior;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionCheck;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.junit.jupiter.api.Test;

class AppendCurrentProblemNotePermissionHookTest {

  private static final long USER_ID = 7L;
  private static final long SESSION_ID = 50L;
  private static final String PROBLEM_SLUG = "two-sum";

  @Test
  void asksWithTheExactNormalizedMarkdownAndTrustedProblem() {
    AppendCurrentProblemNotePermissionHook hook = new AppendCurrentProblemNotePermissionHook(
        new StubPracticeSessionRepository(session()));

    var plan = hook.evaluate(check(AppendCurrentProblemNoteAgentToolContracts.TOOL_NAME,
        arguments("  **关键点**：先查补数。  "), metadata()));

    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.ASK);
    assertThat(plan.displayName()).isEqualTo(AppendCurrentProblemNotePermissionHook.DISPLAY_NAME);
    assertThat(plan.reason()).isEqualTo(AppendCurrentProblemNotePermissionHook.REASON);
    assertThat(plan.metadata()).containsEntry(
        AgentToolPermissionMetadataKeys.COPY_CODE,
        PracticeAgentToolPermissionCopyCodes.APPEND_CURRENT_PROBLEM_NOTE_REQUESTED);
    assertThat(plan.preview())
        .containsEntry(AppendCurrentProblemNoteAgentToolContracts.PREVIEW_NOTE_MARKDOWN, "**关键点**：先查补数。")
        .containsEntry(AppendCurrentProblemNoteAgentToolContracts.PREVIEW_PROBLEM_SLUG, PROBLEM_SLUG)
        .containsEntry(AppendCurrentProblemNoteAgentToolContracts.PREVIEW_CONTEXT_AVAILABLE, true);
  }

  @Test
  void invalidArgumentsAreDeniedWithoutRequestingConfirmation() {
    AppendCurrentProblemNotePermissionHook hook = new AppendCurrentProblemNotePermissionHook(
        new StubPracticeSessionRepository(session()));
    JsonNode invalidArguments = JsonNodeFactory.instance.objectNode()
        .put(AppendCurrentProblemNoteAgentToolContracts.ARGUMENT_CONTENT_MARKDOWN, " ")
        .put("problemSlug", "other-problem");

    var plan = hook.evaluate(check(
        AppendCurrentProblemNoteAgentToolContracts.TOOL_NAME, invalidArguments, metadata()));

    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.DENY);
    assertThat(plan.reason()).isEqualTo(AppendCurrentProblemNotePermissionHook.INVALID_ARGUMENT_REASON);
  }

  @Test
  void missingTrustedContextStillShowsTheExactContentForConfirmation() {
    AppendCurrentProblemNotePermissionHook hook = new AppendCurrentProblemNotePermissionHook(
        new StubPracticeSessionRepository(null));

    var plan = hook.evaluate(check(
        AppendCurrentProblemNoteAgentToolContracts.TOOL_NAME,
        arguments("保存这个边界条件"),
        Map.of(AgentRuntimeMetadataKeys.USER_ID, USER_ID)));

    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.ASK);
    assertThat(plan.preview())
        .containsEntry(AppendCurrentProblemNoteAgentToolContracts.PREVIEW_NOTE_MARKDOWN, "保存这个边界条件")
        .containsEntry(AppendCurrentProblemNoteAgentToolContracts.PREVIEW_CONTEXT_AVAILABLE, false);
  }

  @Test
  void unrelatedToolsPassThrough() {
    AppendCurrentProblemNotePermissionHook hook = new AppendCurrentProblemNotePermissionHook(
        new StubPracticeSessionRepository(session()));

    var plan = hook.evaluate(check("calculator", arguments("内容"), metadata()));

    assertThat(plan.behavior()).isEqualTo(AgentToolPermissionBehavior.PASSTHROUGH);
  }

  private AgentToolPermissionCheck check(String toolName, JsonNode arguments, Map<String, Object> metadata) {
    AgentRequest request = new AgentRequest(
        "run-1", "request-1", List.of(LlmMessage.user("把这个记下来")), metadata);
    AgentLoopContext context = new AgentLoopContext("run-1", request, 4, request.metadata());
    return new AgentToolPermissionCheck(
        context,
        1,
        new LlmToolCall("call-1", toolName, arguments),
        tool(toolName),
        request.metadata());
  }

  private JsonNode arguments(String contentMarkdown) {
    return JsonNodeFactory.instance.objectNode()
        .put(AppendCurrentProblemNoteAgentToolContracts.ARGUMENT_CONTENT_MARKDOWN, contentMarkdown);
  }

  private Map<String, Object> metadata() {
    return Map.of(
        AgentRuntimeMetadataKeys.USER_ID, USER_ID,
        PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, SESSION_ID);
  }

  private AgentTool tool(String toolName) {
    return new AgentTool() {
      @Override
      public LlmToolSpec spec() {
        return new LlmToolSpec(
            toolName,
            "test",
            JsonNodeFactory.instance.objectNode().put("type", "object"),
            true);
      }

      @Override
      public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
        return JsonNodeFactory.instance.objectNode().put("ok", true);
      }
    };
  }

  private PracticeSession session() {
    Instant now = Instant.parse("2026-07-31T12:00:00Z");
    return new PracticeSession(
        SESSION_ID,
        USER_ID,
        12L,
        1,
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

  private static final class StubPracticeSessionRepository implements PracticeSessionRepository {
    private final PracticeSession session;

    private StubPracticeSessionRepository(PracticeSession session) {
      this.session = session;
    }

    @Override
    public Optional<PracticeSession> findSessionForUser(long sessionId, long userId) {
      return Optional.ofNullable(session)
          .filter(value -> value.id() == sessionId && value.userId() == userId);
    }

    @Override public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex,
        String problemSlug) { throw new UnsupportedOperationException(); }
    @Override public PracticeSession upsertAndLockSession(long userId, long planId, int phaseIndex,
        String problemSlug, String locale) { throw new UnsupportedOperationException(); }
    @Override public PracticeSession attachAgentTask(long sessionId, long agentTaskId) {
      throw new UnsupportedOperationException();
    }
    @Override public PracticeSession attachProblemStatementMessage(long sessionId, long messageId) {
      throw new UnsupportedOperationException();
    }
    @Override public PracticeProgress updateProgressStatus(long sessionId, long userId,
        PracticeProgressStatus status) { throw new UnsupportedOperationException(); }
    @Override public void touchLastMessageAt(long sessionId) { throw new UnsupportedOperationException(); }
  }
}
