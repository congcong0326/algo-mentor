package org.congcong.algomentor.ops.observability;

public interface AgentOpsRecorder {

  void runStarted(AgentOpsSource source);

  void runCompleted(AgentOpsSource source);

  void runFailed(AgentOpsSource source);

  void toolPermissionDecision(String decision);

  void toolExecution(String toolName, OpsStatus status);

}
