package org.congcong.algomentor.mentor.application;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.domain.learning.LearningTopic;
import org.congcong.algomentor.mentor.application.topic.TopicExplanationAgentDefinition;
import org.congcong.algomentor.mentor.application.topic.TopicExplanationAgentInput;

/** Topic 讲解入口，仅保留业务校验并统一调用 Agent Runtime。 */
public class ExplainTopicUseCase {

  private static final long LEGACY_DEFAULT_USER_ID = 1L;

  private final AgentRuntime agentRuntime;

  public ExplainTopicUseCase(AgentRuntime agentRuntime) {
    this.agentRuntime = agentRuntime;
  }

  public String explain(String topic) {
    return explain(LEGACY_DEFAULT_USER_ID, topic);
  }

  public String explain(long userId, String topic) {
    AgentRunResult result = agentRuntime.execute(invocation(userId, LearningTopic.of(topic), false));
    return result.output() == null ? "" : result.output().text();
  }

  public Flow.Publisher<AgentStreamEvent> stream(String topic) {
    return stream(topic, LEGACY_DEFAULT_USER_ID);
  }

  public Flow.Publisher<AgentStreamEvent> stream(String topic, long userId) {
    return agentRuntime.stream(invocation(userId, LearningTopic.of(topic), true));
  }

  private AgentInvocation<TopicExplanationAgentInput> invocation(
      long userId,
      LearningTopic topic,
      boolean streaming
  ) {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    String idempotencyKey = "topic-" + UUID.randomUUID();
    TopicExplanationAgentInput input = new TopicExplanationAgentInput(
        userId,
        topic,
        idempotencyKey,
        Map.of("topicCharCount", topic.title().length()));
    return new AgentInvocation<>(
        TopicExplanationAgentDefinition.KEY,
        input,
        new AgentInvocationContext(
            userId,
            AgentInvocationMode.USER_ENTRY,
            idempotencyKey,
            null,
            null,
            topic.title().getBytes(StandardCharsets.UTF_8).length,
            streaming));
  }

}
