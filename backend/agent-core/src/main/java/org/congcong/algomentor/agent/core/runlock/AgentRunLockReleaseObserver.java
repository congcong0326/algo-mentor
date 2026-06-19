package org.congcong.algomentor.agent.core.runlock;

import java.util.Map;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentRunResult;

public class AgentRunLockReleaseObserver implements AgentLoopObserver {

  private final AgentRunLockManager lockManager;

  public AgentRunLockReleaseObserver(AgentRunLockManager lockManager) {
    if (lockManager == null) {
      throw new IllegalArgumentException("Agent run lock manager must not be null");
    }
    this.lockManager = lockManager;
  }

  @Override
  public void onRunEnd(AgentLoopContext context, AgentRunResult result) {
    release(context);
  }

  @Override
  public void onError(AgentLoopContext context, AgentException error) {
    release(context);
  }

  private void release(AgentLoopContext context) {
    Object token = context.metadata().get(AgentRunLockConstants.LOCK_TOKEN_METADATA_KEY);
    if (token instanceof AgentRunLockToken lockToken) {
      lockManager.release(lockToken);
      return;
    }
    if (token instanceof Map<?, ?> tokenMap) {
      lockManager.release(AgentRunLockToken.fromMetadataMap(tokenMap));
    }
  }
}
