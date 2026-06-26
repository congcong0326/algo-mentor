package org.congcong.algomentor.agent.core.permission;

import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

public final class AgentToolPermissionGuard {

  private final AgentToolPermissionHookChain hookChain;
  private final AgentToolPermissionCoordinator coordinator;

  public AgentToolPermissionGuard(
      AgentToolPermissionHookChain hookChain,
      AgentToolPermissionCoordinator coordinator
  ) {
    this.hookChain = Objects.requireNonNull(hookChain, "hookChain must not be null");
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
  }

  public AgentToolPermissionAuthorization authorize(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      AgentTool tool
  ) {
    return authorize(context, stepIndex, toolCall, tool, AgentToolPermissionCoordinator.EventPublisher.noop());
  }

  public AgentToolPermissionAuthorization authorize(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      AgentTool tool,
      AgentToolPermissionCoordinator.EventPublisher eventPublisher
  ) {
    AgentToolPermissionCheck check = new AgentToolPermissionCheck(
        context,
        stepIndex,
        toolCall,
        tool,
        context.request().metadata());
    AgentToolPermissionDecisionPlan plan = hookChain.evaluate(check);
    return coordinator.authorize(check, plan, context.cancellationToken(), eventPublisher);
  }
}
