package org.congcong.algomentor.agent.core;

import org.congcong.algomentor.domain.learning.LearningTopic;

public record AgentRequest(LearningTopic topic) {

  public AgentRequest {
    if (topic == null) {
      throw new IllegalArgumentException("Agent request topic must not be null");
    }
  }
}
