package org.congcong.algomentor.agent.core;

public record AgentResponse(String content) {

  public AgentResponse {
    if (content == null) {
      throw new IllegalArgumentException("Agent response content must not be null");
    }
  }
}
