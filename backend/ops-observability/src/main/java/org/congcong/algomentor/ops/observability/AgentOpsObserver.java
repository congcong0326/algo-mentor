package org.congcong.algomentor.ops.observability;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecision;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionPlan;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentOpsObserver implements AgentLoopObserver {

  private static final Logger log = LoggerFactory.getLogger(AgentOpsObserver.class);
  private static final AgentOpsSource FALLBACK_SOURCE = AgentOpsSource.AGENT_CONVERSATION;
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

    return AgentOpsSource.fromAgentKey(context.metadata().get(AgentRuntimeMetadataKeys.AGENT_KEY));
  }

}
