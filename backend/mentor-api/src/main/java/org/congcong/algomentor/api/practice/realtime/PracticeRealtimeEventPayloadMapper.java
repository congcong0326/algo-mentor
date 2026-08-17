package org.congcong.algomentor.api.practice.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.AgentStreamEventNames;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentToolNames;
import org.congcong.algomentor.mentor.application.practice.ProposeCurrentProblemCoachSummaryAgentToolContracts;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerDeclaredProfileToolContracts;
import org.springframework.stereotype.Component;

/**
 * Practice Chat realtime 边界的公开 DTO 投影器。
 *
 * <p>这里是 Agent 运行 metadata、原始工具结果和浏览器之间的唯一边界。未知事件和工具默认不公开，
 * 因此新增 Agent 能力不会意外扩大 SSE 载荷。</p>
 */
@Component
public final class PracticeRealtimeEventPayloadMapper {

  /** 前端已识别的容量错误；其余内部错误统一投影为该稳定码。 */
  public static final String PRACTICE_RUN_FAILED_CODE = "PRACTICE_RUN_FAILED";
  /** 不使用异常原文的固定用户文案，前端可按错误码替换为本地化版本。 */
  public static final String PRACTICE_RUN_FAILED_MESSAGE = "本次 AI 回复未能完成，请稍后重试。";

  public Optional<PracticeRealtimeEventPayload> map(AgentStreamEvent event) {
    if (event instanceof AgentStreamEvent.AgentStepStart start) {
      return Optional.of(payload(AgentStreamEventNames.AGENT_STEP_START, step(start.runId(), start.stepIndex())));
    }
    if (event instanceof AgentStreamEvent.AgentStepEnd end) {
      ObjectNode data = step(end.runId(), end.stepIndex());
      data.put("finishReason", end.finishReason().name());
      data.put("toolCallCount", end.toolCallCount());
      return Optional.of(payload(AgentStreamEventNames.AGENT_STEP_END, data));
    }
    if (event instanceof AgentStreamEvent.AgentRunEnd end) {
      ObjectNode data = object();
      data.put("runId", end.runId());
      data.put("steps", end.steps());
      data.put("finishReason", end.finishReason().name());
      return Optional.of(payload(AgentStreamEventNames.AGENT_RUN_END, data));
    }
    if (event instanceof AgentStreamEvent.AgentError error) {
      ObjectNode data = object();
      data.put("runId", error.runId());
      data.put("code", publicErrorCode(error.error().code()));
      data.put("message", PRACTICE_RUN_FAILED_MESSAGE);
      data.put("retryable", error.error().retryable());
      return Optional.of(payload(AgentStreamEventNames.AGENT_ERROR, data));
    }
    if (event instanceof AgentStreamEvent.AgentToolStart start && isPublicTool(start.toolName())) {
      return Optional.of(payload(AgentStreamEventNames.AGENT_TOOL_START,
          tool(start.runId(), start.stepIndex(), start.toolCallId(), start.toolName())));
    }
    if (event instanceof AgentStreamEvent.AgentToolEnd end && isPublicTool(end.toolName())) {
      ObjectNode data = tool(end.runId(), end.stepIndex(), end.toolCallId(), end.toolName());
      data.set("result", publicToolResult(end.toolName(), end.result()));
      return Optional.of(payload(AgentStreamEventNames.AGENT_TOOL_END, data));
    }
    if (event instanceof AgentStreamEvent.Llm llm && llm.event() instanceof LlmStreamEvent.ContentDelta delta) {
      ObjectNode data = object();
      data.put("content", delta.content());
      return Optional.of(payload(AgentStreamEventNames.CONTENT_DELTA, data));
    }
    return Optional.empty();
  }

  private PracticeRealtimeEventPayload payload(String eventName, ObjectNode data) {
    return new PracticeRealtimeEventPayload(eventName, data);
  }

  private ObjectNode step(String runId, int stepIndex) {
    ObjectNode data = object();
    data.put("runId", runId);
    data.put("stepIndex", stepIndex);
    return data;
  }

  private ObjectNode tool(String runId, int stepIndex, String toolCallId, String toolName) {
    ObjectNode data = step(runId, stepIndex);
    data.put("toolCallId", toolCallId);
    data.put("toolName", toolName);
    return data;
  }

  private JsonNode publicToolResult(String toolName, JsonNode result) {
    if (PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW.equals(toolName)) {
      return reviewResult(result);
    }
    if (ProposeCurrentProblemCoachSummaryAgentToolContracts.TOOL_NAME.equals(toolName)) {
      return coachSummaryProposalResult(result);
    }
    return learnerDeclaredProfileResult(result);
  }

  private ObjectNode reviewResult(JsonNode source) {
    ObjectNode result = object();
    copyTextField(source, result, PracticeCodeReviewAgentToolNames.RESULT_TYPE);
    copyTextField(source, result, PracticeCodeReviewAgentToolNames.RESULT_STATUS);
    copyFiniteNumberField(source, result, PracticeCodeReviewAgentToolNames.RESULT_TOTAL_SCORE);
    copyBooleanField(source, result, PracticeCodeReviewAgentToolNames.RESULT_PASSED);
    copyTextField(source, result, PracticeCodeReviewAgentToolNames.RESULT_FAILURE_CODE);
    return result;
  }

  private ObjectNode coachSummaryProposalResult(JsonNode source) {
    ObjectNode result = object();
    copyTextField(source, result, ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_TYPE);
    copyTextField(source, result, ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_STATUS);
    copyTextField(source, result, ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_PROPOSAL_ID);
    copyTextField(source, result, ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_SUMMARY_MARKDOWN);
    copyTextField(source, result, ProposeCurrentProblemCoachSummaryAgentToolContracts.FIELD_OPERATION);
    return result;
  }

  private ObjectNode learnerDeclaredProfileResult(JsonNode source) {
    ObjectNode result = object();
    copyTextField(source, result, LearnerDeclaredProfileToolContracts.RESULT_FIELD_TYPE);
    copyTextField(source, result, LearnerDeclaredProfileToolContracts.RESULT_FIELD_STATUS);
    return result;
  }

  private void copyTextField(JsonNode source, ObjectNode target, String fieldName) {
    JsonNode value = field(source, fieldName);
    if (value != null && value.isTextual()) {
      target.set(fieldName, value);
    }
  }

  private void copyFiniteNumberField(JsonNode source, ObjectNode target, String fieldName) {
    JsonNode value = field(source, fieldName);
    if (value != null && value.isNumber()
        && (!value.isFloatingPointNumber() || Double.isFinite(value.doubleValue()))) {
      target.set(fieldName, value);
    }
  }

  private void copyBooleanField(JsonNode source, ObjectNode target, String fieldName) {
    JsonNode value = field(source, fieldName);
    if (value != null && value.isBoolean()) {
      target.set(fieldName, value);
    }
  }

  private JsonNode field(JsonNode source, String fieldName) {
    return source != null && source.isObject() ? source.get(fieldName) : null;
  }

  private boolean isPublicTool(String toolName) {
    return PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW.equals(toolName)
        || ProposeCurrentProblemCoachSummaryAgentToolContracts.TOOL_NAME.equals(toolName)
        || LearnerDeclaredProfileToolContracts.TOOL_NAME.equals(toolName);
  }

  private String publicErrorCode(AgentErrorCode code) {
    return code == AgentErrorCode.AGENT_EXECUTOR_OVERLOADED
        ? AgentErrorCode.AGENT_EXECUTOR_OVERLOADED.name()
        : PRACTICE_RUN_FAILED_CODE;
  }

  private ObjectNode object() {
    return JsonNodeFactory.instance.objectNode();
  }
}
