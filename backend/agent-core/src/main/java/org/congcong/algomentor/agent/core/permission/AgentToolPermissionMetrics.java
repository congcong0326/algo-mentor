package org.congcong.algomentor.agent.core.permission;

import java.time.Duration;

public interface AgentToolPermissionMetrics {

  String HOOK_DECISIONS = "agent.tool.permission.hook.decisions";
  String PERMISSION_REQUESTS = "agent.tool.permission.requests";
  String USER_DECISIONS = "agent.tool.permission.decisions";
  String TIMEOUTS = "agent.tool.permission.timeouts";
  String LATENCY = "agent.tool.permission.latency";
  String HIGH_PERMISSION_EXECUTION = "agent.tool.execution.high_permission";

  String TAG_TOOL_NAME = "toolName";
  String TAG_BEHAVIOR = "behavior";
  String TAG_POLICY_SOURCE = "policySource";
  String TAG_DECISION = "decision";
  String TAG_OUTCOME = "outcome";

  String OUTCOME_ALLOW = "allow";
  String OUTCOME_DENY = "deny";
  String OUTCOME_TIMEOUT = "timeout";
  String OUTCOME_CANCELLED = "cancelled";

  void recordHookDecision(
      String toolName,
      AgentToolPermissionBehavior behavior,
      String policySource
  );

  void recordPermissionRequest(
      String toolName,
      String policySource
  );

  void recordUserDecision(
      String toolName,
      AgentToolPermissionDecisionType decision
  );

  void recordTimeout(String toolName);

  void recordLatency(
      String toolName,
      String outcome,
      Duration latency
  );

  void recordHighPermissionExecution(
      String toolName,
      String policySource
  );
}
