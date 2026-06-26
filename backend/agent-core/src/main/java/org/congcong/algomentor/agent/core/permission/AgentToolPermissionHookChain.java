package org.congcong.algomentor.agent.core.permission;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AgentToolPermissionHookChain {

  private static final Logger log = LoggerFactory.getLogger(AgentToolPermissionHookChain.class);

  private static final String HOOK_FAILURE_POLICY_SOURCE_PREFIX = "permission-hook-failure:";
  private static final String HOOK_FAILURE_REASON = "permission_hook_failed";

  private final List<AgentToolPermissionHook> hooks;
  private final DefaultPermissionHook defaultPermissionHook = new DefaultPermissionHook();
  private final AgentToolPermissionMetrics metrics;

  public AgentToolPermissionHookChain() {
    this(List.of(), NoopAgentToolPermissionMetrics.INSTANCE);
  }

  public AgentToolPermissionHookChain(List<AgentToolPermissionHook> hooks) {
    this(hooks, NoopAgentToolPermissionMetrics.INSTANCE);
  }

  public AgentToolPermissionHookChain(
      List<AgentToolPermissionHook> hooks,
      AgentToolPermissionMetrics metrics
  ) {
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    if (hooks == null) {
      this.hooks = List.of();
      return;
    }
    this.hooks = hooks.stream()
        .peek(hook -> {
          if (hook == null) {
            throw new IllegalArgumentException("Agent tool permission hook must not be null");
          }
        })
        .sorted(Comparator.comparingInt(AgentToolPermissionHook::order))
        .toList();
  }

  public AgentToolPermissionDecisionPlan evaluate(AgentToolPermissionCheck check) {
    if (check == null) {
      throw new IllegalArgumentException("Agent tool permission check must not be null");
    }

    for (AgentToolPermissionHook hook : hooks) {
      AgentToolPermissionDecisionPlan plan;
      try {
        plan = hook.evaluate(check);
      } catch (RuntimeException ex) {
        AgentToolPermissionDecisionPlan failurePlan = hookFailurePlan(hook, errorType(ex));
        recordDecision(check, failurePlan);
        logHookDecision(check, failurePlan);
        return failurePlan;
      }
      if (plan == null) {
        AgentToolPermissionDecisionPlan failurePlan =
            hookFailurePlan(hook, NullPointerException.class.getSimpleName());
        recordDecision(check, failurePlan);
        logHookDecision(check, failurePlan);
        return failurePlan;
      }
      if (plan.behavior() != AgentToolPermissionBehavior.PASSTHROUGH) {
        recordDecision(check, plan);
        logHookDecision(check, plan);
        return plan;
      }
    }

    AgentToolPermissionDecisionPlan defaultPlan = defaultPermissionHook.evaluate(check);
    recordDecision(check, defaultPlan);
    logHookDecision(check, defaultPlan);
    return defaultPlan;
  }

  public List<AgentToolPermissionHook> hooks() {
    return hooks;
  }

  private void recordDecision(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan
  ) {
    metrics.recordHookDecision(check.toolCall().name(), plan.behavior(), plan.policySource());
  }

  private static void logHookDecision(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan
  ) {
    if (!log.isInfoEnabled()) {
      return;
    }
    log.info(
        "Agent tool permission hook decision runId={} toolName={} toolCallId={} decision={} reason={}",
        check.context().runId(),
        check.toolCall().name(),
        check.toolCall().id(),
        plan.behavior(),
        plan.reason());
  }

  private static AgentToolPermissionDecisionPlan hookFailurePlan(
      AgentToolPermissionHook hook,
      String errorType
  ) {
    String hookName = hookName(hook);
    return AgentToolPermissionDecisionPlan.deny(
        HOOK_FAILURE_REASON,
        HOOK_FAILURE_POLICY_SOURCE_PREFIX + hookName,
        Map.of(
            AgentToolPermissionMetadataKeys.PERMISSION_HOOK_NAME,
            hookName,
            AgentToolPermissionMetadataKeys.PERMISSION_HOOK_ERROR_TYPE,
            errorType));
  }

  private static String hookName(AgentToolPermissionHook hook) {
    String simpleName = hook.getClass().getSimpleName();
    if (simpleName != null && !simpleName.isBlank()) {
      return simpleName;
    }
    return hook.getClass().getName();
  }

  private static String errorType(RuntimeException ex) {
    String simpleName = ex.getClass().getSimpleName();
    if (simpleName != null && !simpleName.isBlank()) {
      return simpleName;
    }
    return ex.getClass().getName();
  }
}
