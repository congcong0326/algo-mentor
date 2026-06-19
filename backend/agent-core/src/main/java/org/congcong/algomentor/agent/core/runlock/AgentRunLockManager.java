package org.congcong.algomentor.agent.core.runlock;

public interface AgentRunLockManager {

  AgentRunLockAcquireResult tryAcquire(AgentRunLockRequest request);

  boolean refresh(AgentRunLockToken token);

  void release(AgentRunLockToken token);
}
