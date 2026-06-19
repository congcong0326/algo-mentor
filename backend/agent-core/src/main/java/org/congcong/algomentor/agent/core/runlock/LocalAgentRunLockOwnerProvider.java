package org.congcong.algomentor.agent.core.runlock;

public class LocalAgentRunLockOwnerProvider implements AgentRunLockOwnerProvider {

  public static final String DEFAULT_OWNER_ID = "local-agent-api";

  private final String ownerId;

  public LocalAgentRunLockOwnerProvider() {
    this(DEFAULT_OWNER_ID);
  }

  public LocalAgentRunLockOwnerProvider(String ownerId) {
    if (ownerId == null || ownerId.isBlank()) {
      throw new IllegalArgumentException("Agent run lock owner id must not be blank");
    }
    this.ownerId = ownerId;
  }

  @Override
  public String ownerId() {
    return ownerId;
  }
}
