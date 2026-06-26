package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewAgentToolTest {

  private static final long USER_ID = 7L;
  private static final long SESSION_ID = 50L;
  private static final long PLAN_ID = 12L;
  private static final int PHASE_INDEX = 1;
  private static final long RUN_DB_ID = 501L;
  private static final long USER_MESSAGE_ID = 701L;
  private static final long ASSISTANT_MESSAGE_ID = 702L;
  private static final String PROBLEM_SLUG = "climbing-stairs";
  private static final String USER_MESSAGE_CONTENT =
      "class Solution { public int climbStairs(int n) { return n; } }";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void specOnlyExposesOptionalIntentAndNotes() {
    PracticeCodeReviewAgentTool tool = tool(
        new FakePracticeSessionRepository(),
        new FakeTurnMessageLookupRepository(turnMessages()),
        new CapturingReviewService(PracticeReviewResult.saved(review())));

    LlmToolSpec spec = tool.spec();

    assertThat(spec.name()).isEqualTo(PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW);
    JsonNode schema = spec.inputSchema();
    assertThat(schema.path("type").asText()).isEqualTo("object");
    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.path("required")).isEmpty();
    assertThat(schema.path("properties").fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            PracticeCodeReviewAgentToolNames.ARGUMENT_USER_INTENT,
            PracticeCodeReviewAgentToolNames.ARGUMENT_NOTES);
    assertThat(schema.toString())
        .doesNotContain("userId")
        .doesNotContain("sessionId")
        .doesNotContain("problemSlug")
        .doesNotContain("code");
  }

  @Test
  void executeBuildsTrustedTurnContextAndReturnsStableJson() {
    CapturingReviewService reviewService = new CapturingReviewService(PracticeReviewResult.saved(review()));
    PracticeCodeReviewAgentTool tool = tool(
        new FakePracticeSessionRepository(),
        new FakeTurnMessageLookupRepository(turnMessages()),
        reviewService);
    ObjectNode arguments = objectMapper.createObjectNode()
        .put(PracticeCodeReviewAgentToolNames.ARGUMENT_USER_INTENT, "请帮我正式 review")
        .put(PracticeCodeReviewAgentToolNames.ARGUMENT_NOTES, "模型侧备注");

    JsonNode result = tool.execute(arguments, executionContext(metadata()));

    assertThat(reviewService.contexts).singleElement().satisfies(context -> {
      assertThat(context.userId()).isEqualTo(USER_ID);
      assertThat(context.sessionId()).isEqualTo(SESSION_ID);
      assertThat(context.planId()).isEqualTo(PLAN_ID);
      assertThat(context.phaseIndex()).isEqualTo(PHASE_INDEX);
      assertThat(context.problemSlug()).isEqualTo(PROBLEM_SLUG);
      assertThat(context.userMessageId()).isEqualTo(USER_MESSAGE_ID);
      assertThat(context.assistantMessageId()).isEqualTo(ASSISTANT_MESSAGE_ID);
      assertThat(context.agentRunDbId()).isEqualTo(RUN_DB_ID);
      assertThat(context.originalMessage()).isEqualTo(USER_MESSAGE_CONTENT);
      assertThat(context.extractedCode()).isEqualTo(USER_MESSAGE_CONTENT);
    });
    assertThat(result.path(PracticeCodeReviewAgentToolNames.RESULT_TYPE).asText())
        .isEqualTo(PracticeCodeReviewAgentToolNames.RESULT_TYPE_PRACTICE_CODE_REVIEW_SUBMITTED);
    assertThat(result.path(PracticeCodeReviewAgentToolNames.RESULT_STATUS).asText())
        .isEqualTo(PracticeReviewStatus.SAVED.name());
    assertThat(result.path(PracticeCodeReviewAgentToolNames.RESULT_REVIEW_ID).asLong()).isEqualTo(900L);
    assertThat(result.path(PracticeCodeReviewAgentToolNames.RESULT_VERSION_NO).asInt()).isEqualTo(3);
    assertThat(result.path(PracticeCodeReviewAgentToolNames.RESULT_TOTAL_SCORE).decimalValue())
        .isEqualByComparingTo(new BigDecimal("8.0"));
    assertThat(result.path(PracticeCodeReviewAgentToolNames.RESULT_PASSED).asBoolean()).isTrue();
    assertThat(result.path(PracticeCodeReviewAgentToolNames.RESULT_FAILURE_CODE).isNull()).isTrue();
    assertThat(result.path(PracticeCodeReviewAgentToolNames.RESULT_PROBLEM_SLUG).asText()).isEqualTo(PROBLEM_SLUG);
    assertThat(result.path(PracticeCodeReviewAgentToolNames.RESULT_SESSION_ID).asLong()).isEqualTo(SESSION_ID);
    assertThat(result.path(PracticeCodeReviewAgentToolNames.RESULT_USER_MESSAGE_ID).asLong())
        .isEqualTo(USER_MESSAGE_ID);
    assertThat(result.path(PracticeCodeReviewAgentToolNames.RESULT_AGENT_RUN_DB_ID).asLong()).isEqualTo(RUN_DB_ID);
    assertThat(result.path(PracticeCodeReviewAgentToolNames.RESULT_MESSAGE).asText()).isNotBlank();
    assertThat(result.has("rawCode")).isFalse();
    assertThat(result.has("normalizedCode")).isFalse();
    assertThat(result.has("reviewMarkdown")).isFalse();
  }

  @Test
  void executeRejectsUntrustedIdentityOrCodeArguments() {
    CapturingReviewService reviewService = new CapturingReviewService(PracticeReviewResult.saved(review()));
    PracticeCodeReviewAgentTool tool = tool(
        new FakePracticeSessionRepository(),
        new FakeTurnMessageLookupRepository(turnMessages()),
        reviewService);
    ObjectNode arguments = objectMapper.createObjectNode()
        .put(PracticeCodeReviewAgentToolNames.ARGUMENT_USER_INTENT, "请帮我正式 review")
        .put("userId", 999L)
        .put("code", "malicious code from model arguments");

    assertThatThrownBy(() -> tool.execute(arguments, executionContext(metadata())))
        .isInstanceOfSatisfying(AgentException.class, exception -> {
          assertThat(exception.code()).isEqualTo(AgentErrorCode.TOOL_EXECUTION_FAILED);
          assertThat(exception.metadata())
              .containsEntry(AgentRuntimeMetadataKeys.ERROR_TYPE, "INVALID_ARGUMENTS");
        });
    assertThat(reviewService.contexts).isEmpty();
  }

  @Test
  void executeFailsWhenRequiredMetadataIsMissing() {
    CapturingReviewService reviewService = new CapturingReviewService(PracticeReviewResult.saved(review()));
    PracticeCodeReviewAgentTool tool = tool(
        new FakePracticeSessionRepository(),
        new FakeTurnMessageLookupRepository(turnMessages()),
        reviewService);
    Map<String, Object> metadata = Map.of(
        PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO,
        AgentRuntimeMetadataKeys.USER_ID, USER_ID,
        PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, SESSION_ID);

    assertThatThrownBy(() -> tool.execute(objectMapper.createObjectNode(), executionContext(metadata)))
        .isInstanceOfSatisfying(AgentException.class, exception -> {
          assertThat(exception.code()).isEqualTo(AgentErrorCode.TOOL_EXECUTION_FAILED);
          assertThat(exception.metadata())
              .containsEntry(AgentRuntimeMetadataKeys.TOOL_NAME,
                  PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW)
              .containsEntry(AgentRuntimeMetadataKeys.ERROR_TYPE, "MISSING_METADATA");
    });
    assertThat(reviewService.contexts).isEmpty();
  }

  @Test
  void executeFailsOutsidePracticeChatScenario() {
    CapturingReviewService reviewService = new CapturingReviewService(PracticeReviewResult.saved(review()));
    PracticeCodeReviewAgentTool tool = tool(
        new FakePracticeSessionRepository(),
        new FakeTurnMessageLookupRepository(turnMessages()),
        reviewService);
    Map<String, Object> metadata = Map.of(
        PracticeChatPromptConstants.METADATA_SCENARIO, "TOPIC_CHAT",
        AgentRuntimeMetadataKeys.USER_ID, USER_ID,
        PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, SESSION_ID,
        AgentRuntimeMetadataKeys.RUN_DB_ID, RUN_DB_ID);

    assertThatThrownBy(() -> tool.execute(objectMapper.createObjectNode(), executionContext(metadata)))
        .isInstanceOfSatisfying(AgentException.class, exception -> {
          assertThat(exception.code()).isEqualTo(AgentErrorCode.TOOL_EXECUTION_FAILED);
          assertThat(exception.metadata())
              .containsEntry(AgentRuntimeMetadataKeys.ERROR_TYPE, "NOT_PRACTICE_CHAT");
        });
    assertThat(reviewService.contexts).isEmpty();
  }

  @Test
  void executeFailsWhenSessionDoesNotBelongToUserOrDoesNotExist() {
    CapturingReviewService reviewService = new CapturingReviewService(PracticeReviewResult.saved(review()));
    PracticeCodeReviewAgentTool tool = tool(
        new MissingPracticeSessionRepository(),
        new FakeTurnMessageLookupRepository(turnMessages()),
        reviewService);

    assertThatThrownBy(() -> tool.execute(objectMapper.createObjectNode(), executionContext(metadata())))
        .isInstanceOfSatisfying(AgentException.class, exception -> {
          assertThat(exception.code()).isEqualTo(AgentErrorCode.TOOL_EXECUTION_FAILED);
          assertThat(exception.metadata())
              .containsEntry(AgentRuntimeMetadataKeys.ERROR_TYPE, "PRACTICE_SESSION_NOT_FOUND");
        });
    assertThat(reviewService.contexts).isEmpty();
  }

  @Test
  void executeFailsWhenTurnMessageLookupIsMissing() {
    CapturingReviewService reviewService = new CapturingReviewService(PracticeReviewResult.saved(review()));
    PracticeCodeReviewAgentTool tool = tool(
        new FakePracticeSessionRepository(),
        new MissingTurnMessageLookupRepository(),
        reviewService);

    assertThatThrownBy(() -> tool.execute(objectMapper.createObjectNode(), executionContext(metadata())))
        .isInstanceOfSatisfying(AgentException.class, exception -> {
          assertThat(exception.code()).isEqualTo(AgentErrorCode.TOOL_EXECUTION_FAILED);
          assertThat(exception.metadata())
              .containsEntry(AgentRuntimeMetadataKeys.ERROR_TYPE, "TURN_MESSAGE_LOOKUP_MISSING");
        });
    assertThat(reviewService.contexts).isEmpty();
  }

  @Test
  void executeReusesExistingReviewForSameUserMessageWithoutCallingLlmOrSavingAgain() {
    ExistingReviewRepository reviewRepository = new ExistingReviewRepository(review());
    CountingLlmGateway llmGateway = new CountingLlmGateway();
    PracticeCodeReviewService reviewService = new PracticeCodeReviewService(
        reviewRepository,
        llmGateway,
        new PracticeCodeReviewPromptBuilder(),
        new PracticeCodeReviewStructuredOutputMapper());
    PracticeCodeReviewAgentTool tool = tool(
        new FakePracticeSessionRepository(),
        new FakeTurnMessageLookupRepository(turnMessages()),
        reviewService);

    JsonNode result = tool.execute(objectMapper.createObjectNode(), executionContext(metadata()));

    assertThat(result.path(PracticeCodeReviewAgentToolNames.RESULT_REVIEW_ID).asLong()).isEqualTo(900L);
    assertThat(result.path(PracticeCodeReviewAgentToolNames.RESULT_VERSION_NO).asInt()).isEqualTo(3);
    assertThat(reviewRepository.findByUserMessageCalls).isEqualTo(1);
    assertThat(reviewRepository.savedDrafts).isEmpty();
    assertThat(llmGateway.completeCalls).isZero();
  }

  private PracticeCodeReviewAgentTool tool(
      PracticeSessionRepository sessionRepository,
      AgentTurnMessageLookupRepository turnMessageLookupRepository,
      PracticeCodeReviewService reviewService
  ) {
    return new PracticeCodeReviewAgentTool(
        sessionRepository,
        turnMessageLookupRepository,
        reviewService,
        objectMapper);
  }

  private AgentExecutionContext executionContext(Map<String, Object> metadata) {
    return new AgentExecutionContext("run-1", 1, metadata, false);
  }

  private Map<String, Object> metadata() {
    return Map.of(
        PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO,
        AgentRuntimeMetadataKeys.USER_ID, USER_ID,
        PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, SESSION_ID,
        AgentRuntimeMetadataKeys.RUN_DB_ID, RUN_DB_ID,
        AgentRuntimeMetadataKeys.TASK_ID, 100L,
        AgentRuntimeMetadataKeys.TURN_ID, 200L);
  }

  private AgentTurnMessages turnMessages() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    AgentMessage userMessage = new AgentMessage(
        USER_MESSAGE_ID,
        100L,
        1L,
        AgentMessage.Role.USER,
        USER_MESSAGE_CONTENT,
        now);
    AgentMessage assistantMessage = new AgentMessage(
        ASSISTANT_MESSAGE_ID,
        100L,
        2L,
        AgentMessage.Role.ASSISTANT,
        "我来看看。",
        now.plusSeconds(1));
    return new AgentTurnMessages(RUN_DB_ID, 200L, userMessage, assistantMessage);
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

  private static PracticeCodeReview review() {
    return new PracticeCodeReview(
        900L,
        USER_ID,
        PLAN_ID,
        PHASE_INDEX,
        PROBLEM_SLUG,
        SESSION_ID,
        3,
        USER_MESSAGE_ID,
        ASSISTANT_MESSAGE_ID,
        RUN_DB_ID,
        USER_MESSAGE_CONTENT,
        USER_MESSAGE_CONTENT,
        "java",
        List.of(new PracticeCodeReviewEvidence("ENTRY_FUNCTION", "climbStairs")),
        "已保存的 Review",
        new PracticeCodeReviewScore(
            new BigDecimal("3.0"),
            new BigDecimal("2.0"),
            new BigDecimal("1.0"),
            new BigDecimal("1.0"),
            new BigDecimal("1.0"),
            new BigDecimal("8.0")),
        true,
        List.of("边界覆盖不足"),
        List.of("补充 n=1 的处理"),
        "整体可通过。",
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  private final class FakePracticeSessionRepository implements PracticeSessionRepository {

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
      return Optional.of(session())
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

  private static final class MissingPracticeSessionRepository implements PracticeSessionRepository {

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
      return Optional.empty();
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

  private static final class FakeTurnMessageLookupRepository implements AgentTurnMessageLookupRepository {
    private final AgentTurnMessages messages;

    private FakeTurnMessageLookupRepository(AgentTurnMessages messages) {
      this.messages = messages;
    }

    @Override
    public Optional<AgentTurnMessages> findByRunId(long runId) {
      return runId == messages.runId() ? Optional.of(messages) : Optional.empty();
    }
  }

  private static final class MissingTurnMessageLookupRepository implements AgentTurnMessageLookupRepository {

    @Override
    public Optional<AgentTurnMessages> findByRunId(long runId) {
      return Optional.empty();
    }
  }

  private static final class CapturingReviewService extends PracticeCodeReviewService {
    private final List<PracticeTurnContext> contexts;

    private CapturingReviewService(PracticeReviewResult result) {
      this(new ArrayList<>(), result);
    }

    private CapturingReviewService(List<PracticeTurnContext> contexts, PracticeReviewResult result) {
      super(context -> {
        contexts.add(context);
        return result;
      });
      this.contexts = contexts;
    }
  }

  private static final class ExistingReviewRepository implements PracticeCodeReviewRepository {
    private final PracticeCodeReview existingReview;
    private final List<PracticeCodeReviewDraft> savedDrafts = new ArrayList<>();
    private int findByUserMessageCalls;

    private ExistingReviewRepository(PracticeCodeReview existingReview) {
      this.existingReview = existingReview;
    }

    @Override
    public PracticeCodeReview save(PracticeCodeReviewDraft draft) {
      savedDrafts.add(draft);
      return existingReview;
    }

    @Override
    public Optional<PracticeCodeReviewSummary> findLatestSummary(long userId, long sessionId) {
      return Optional.empty();
    }

    @Override
    public Optional<PracticeCodeReview> findLatest(long userId, long sessionId) {
      return Optional.empty();
    }

    @Override
    public List<PracticeCodeReviewSummary> findSummaries(long userId, long sessionId) {
      return List.of();
    }

    @Override
    public Optional<PracticeCodeReview> findById(long userId, long sessionId, long reviewId) {
      return Optional.empty();
    }

    @Override
    public Optional<PracticeCodeReview> findByUserMessage(long userId, long sessionId, long userMessageId) {
      findByUserMessageCalls++;
      if (existingReview.userId() == userId
          && existingReview.sessionId() == sessionId
          && existingReview.userMessageId() == userMessageId) {
        return Optional.of(existingReview);
      }
      return Optional.empty();
    }
  }

  private static final class CountingLlmGateway implements LlmGateway {
    private int completeCalls;

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      completeCalls++;
      throw new AssertionError("LLM should not be called when review already exists");
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("stream not used");
    }
  }
}
