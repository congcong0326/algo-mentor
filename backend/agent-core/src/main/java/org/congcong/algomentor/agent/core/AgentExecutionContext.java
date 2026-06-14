package org.congcong.algomentor.agent.core;

import org.congcong.algomentor.domain.learning.LearningTopic;

public record AgentExecutionContext(
    String runId,
    int stepIndex,
    LearningTopic topic,
    boolean cancelled
) {

  public AgentExecutionContext {
    if (runId == null || runId.isBlank()) {
      throw new IllegalArgumentException("Agent execution run id must not be blank");
    }
    if (stepIndex < 1) {
      throw new IllegalArgumentException("Agent execution step index must be positive");
    }
    if (topic == null) {
      throw new IllegalArgumentException("Agent execution topic must not be null");
    }
  }
}
