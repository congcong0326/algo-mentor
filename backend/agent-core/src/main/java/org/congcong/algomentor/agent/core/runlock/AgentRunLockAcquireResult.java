package org.congcong.algomentor.agent.core.runlock;

public record AgentRunLockAcquireResult(
    boolean acquired,
    AgentRunLockToken token,
    AgentRunLockConflict conflict
) {

  public static AgentRunLockAcquireResult acquired(AgentRunLockToken token) {
    if (token == null) {
      throw new IllegalArgumentException("Agent run lock token must not be null");
    }
    return new AgentRunLockAcquireResult(true, token, null);
  }

  public static AgentRunLockAcquireResult conflicted(AgentRunLockConflict conflict) {
    if (conflict == null) {
      throw new IllegalArgumentException("Agent run lock conflict must not be null");
    }
    return new AgentRunLockAcquireResult(false, null, conflict);
  }

  public AgentRunLockAcquireResult {
    if (acquired && token == null) {
      throw new IllegalArgumentException("Acquired agent run lock result must include token");
    }
    if (!acquired && conflict == null) {
      throw new IllegalArgumentException("Conflicted agent run lock result must include conflict");
    }
  }
}
