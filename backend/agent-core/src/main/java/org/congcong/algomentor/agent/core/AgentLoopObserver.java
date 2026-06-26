package org.congcong.algomentor.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecision;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionPlan;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionRequest;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

public interface AgentLoopObserver {

  default void onRunStart(AgentLoopContext context) {}

  default void onStepStart(AgentLoopContext context, int stepIndex) {}

  default void onLlmRequestReady(AgentLoopContext context, int stepIndex, LlmCompletionRequest request) {}

  default void onLlmEvent(AgentLoopContext context, int stepIndex, LlmStreamEvent event) {}

  default void onStepEnd(AgentLoopContext context, int stepIndex, AgentStepResult result) {}

  default void onFinalOutput(AgentLoopContext context, AgentOutput output) {}

  default void onToolStart(AgentLoopContext context, int stepIndex, LlmToolCall toolCall) {}

  default void onToolEnd(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      JsonNode result
  ) {}

  default void onToolPermissionRequest(
      AgentLoopContext context,
      AgentToolPermissionRequest request,
      AgentToolPermissionDecisionPlan plan
  ) {}

  default void onToolPermissionDecision(
      AgentLoopContext context,
      AgentToolPermissionRequest request,
      AgentToolPermissionDecision decision,
      AgentToolPermissionDecisionPlan plan
  ) {}

  default void onToolPermissionTimeout(
      AgentLoopContext context,
      AgentToolPermissionRequest request,
      String reason,
      Instant expiredAt,
      AgentToolPermissionDecisionPlan plan
  ) {}

  default void onToolError(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      AgentException error
  ) {}

  default void onRunEnd(AgentLoopContext context, AgentRunResult result) {}

  default void onError(AgentLoopContext context, AgentException error) {}
}
