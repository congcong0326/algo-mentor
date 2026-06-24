package org.congcong.algomentor.agent.core.runtime.model;

public record AgentTaskRef(long taskId) {

  public AgentTaskRef {
    if (taskId < 1) {
      throw new IllegalArgumentException("Agent task id must be positive");
    }
  }
}
