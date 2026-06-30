package org.congcong.algomentor.ops.observability;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecision;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionPlan;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionRequest;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentOpsObserver implements AgentLoopObserver {

  private static final Logger log = LoggerFactory.getLogger(AgentOpsObserver.class);
  private static final AgentOpsSource FALLBACK_SOURCE = AgentOpsSource.AGENT_CONVERSATION;
  /** AI governance metadata key carrying the low-cardinality run source. */
  private static final String AI_SOURCE_METADATA_KEY = "aiSource";
  /** Legacy source metadata key used by older Agent request payloads. */
  private static final String SOURCE_METADATA_KEY = "source";
  /** AI governance admission metadata key; used only to inspect its source value. */
  private static final String AI_ADMISSION_METADATA_KEY = "aiAdmission";
  /** Synthetic permission decision used when a pending approval expires. */
  private static final String PERMISSION_TIMEOUT_DECISION = "timeout";

  private final AgentOpsRecorder agent;
  private final StructuredOpsLogger opsLogger;

  public AgentOpsObserver(AgentOpsRecorder agent) {
    this(agent, new StructuredOpsLogger());
  }

  AgentOpsObserver(AgentOpsRecorder agent, StructuredOpsLogger opsLogger) {
    this.agent = Objects.requireNonNull(agent, "agent must not be null");
    this.opsLogger = Objects.requireNonNull(opsLogger, "opsLogger must not be null");
  }

  @Override
  public void onRunStart(AgentLoopContext context) {
    agent.runStarted(source(context));
  }

  @Override
  public void onRunEnd(AgentLoopContext context, AgentRunResult result) {
    agent.runCompleted(source(context));
  }

  @Override
  public void onError(AgentLoopContext context, AgentException error) {
    AgentOpsSource agentSource = source(context);
    agent.runFailed(agentSource);
    opsLogger.warn(
        log,
        OpsLogEventType.AGENT_RUN_FAILED,
        Map.of(
            OpsLogFields.AGENT_SOURCE, agentSource.tagValue(),
            OpsLogFields.EXCEPTION_TYPE, error.getClass().getSimpleName()),
        null);
  }

  @Override
  public void onToolEnd(AgentLoopContext context, int stepIndex, LlmToolCall toolCall, JsonNode result) {
    agent.toolExecution(toolCall.name(), OpsStatus.COMPLETED);
  }

  @Override
  public void onToolError(AgentLoopContext context, int stepIndex, LlmToolCall toolCall, AgentException error) {
    agent.toolExecution(toolCall.name(), OpsStatus.FAILED);
  }

  @Override
  public void onToolPermissionDecision(
      AgentLoopContext context,
      AgentToolPermissionRequest request,
      AgentToolPermissionDecision decision,
      AgentToolPermissionDecisionPlan plan) {
    agent.toolPermissionDecision(decision.decision().name());
  }

  @Override
  public void onToolPermissionTimeout(
      AgentLoopContext context,
      AgentToolPermissionRequest request,
      String reason,
      Instant expiredAt,
      AgentToolPermissionDecisionPlan plan) {
    agent.toolPermissionDecision(PERMISSION_TIMEOUT_DECISION);
    opsLogger.warn(
        log,
        OpsLogEventType.AGENT_TOOL_PERMISSION_TIMEOUT,
        Map.of(OpsLogFields.TOOL_NAME, request.toolName()),
        null);
  }

  AgentOpsSource source(AgentLoopContext context) {
    if (context == null) {
      return FALLBACK_SOURCE;
    }

    AgentOpsSource source = source(context.metadata());
    if (source != null) {
      return source;
    }
    return context.request() == null ? FALLBACK_SOURCE : sourceOrFallback(context.request().metadata());
  }

  private AgentOpsSource sourceOrFallback(Map<String, Object> metadata) {
    AgentOpsSource source = source(metadata);
    return source == null ? FALLBACK_SOURCE : source;
  }

  private AgentOpsSource source(Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return null;
    }

    AgentOpsSource source = fromValue(metadata.get(AI_SOURCE_METADATA_KEY));
    if (source != null) {
      return source;
    }
    source = fromValue(metadata.get(SOURCE_METADATA_KEY));
    if (source != null) {
      return source;
    }
    return fromAdmission(metadata.get(AI_ADMISSION_METADATA_KEY));
  }

  private AgentOpsSource fromAdmission(Object admission) {
    if (admission == null) {
      return null;
    }
    try {
      Object source = admission.getClass().getMethod("source").invoke(admission);
      return fromValue(source);
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  private AgentOpsSource fromValue(Object value) {
    if (value == null) {
      return null;
    }

    String source = value instanceof Enum<?> enumValue ? enumValue.name() : String.valueOf(value);
    if (source.isBlank()) {
      return null;
    }

    return switch (source.trim().toUpperCase(Locale.ROOT)) {
      case "PROBLEM_DETAIL", "AI_EXPLANATION" -> AgentOpsSource.AI_EXPLANATION;
      case "LEARNING_PLAN_DRAFT" -> AgentOpsSource.LEARNING_PLAN_DRAFT;
      case "PRACTICE_CHAT", "PRACTICE_MESSAGE" -> AgentOpsSource.PRACTICE_MESSAGE;
      case "LEARNING_CHAT", "AGENT_CONVERSATION" -> AgentOpsSource.AGENT_CONVERSATION;
      default -> null;
    };
  }

}
