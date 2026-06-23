package org.congcong.algomentor.mentor.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.domain.learning.LearningTopic;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.springframework.stereotype.Service;

@Service
public class ExplainTopicUseCase {

  private final AgentRunner agentRunner;
  private final AgentLoopRunner agentLoopRunner;

  public ExplainTopicUseCase(AgentRunner agentRunner, AgentLoopRunner agentLoopRunner) {
    this.agentRunner = agentRunner;
    this.agentLoopRunner = agentLoopRunner;
  }

  public String explain(String topic) {
    return agentRunner.run(toAgentRequest(LearningTopic.of(topic))).content();
  }

  public Flow.Publisher<AgentStreamEvent> stream(String topic) {
    return stream(topic, Map.of());
  }

  public Flow.Publisher<AgentStreamEvent> stream(String topic, Map<String, Object> governanceMetadata) {
    return agentLoopRunner.stream(toAgentRequest(LearningTopic.of(topic), governanceMetadata));
  }

  private AgentRequest toAgentRequest(LearningTopic topic) {
    return toAgentRequest(topic, Map.of());
  }

  private AgentRequest toAgentRequest(LearningTopic topic, Map<String, Object> governanceMetadata) {
    String prompt = "Explain the learning topic for an algorithm student: " + topic.title();
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(AgentRuntimeMetadataKeys.TITLE, topic.title());
    metadata.put(AgentRuntimeMetadataKeys.TOPIC_TITLE, topic.title());
    metadata.put(AgentRuntimeMetadataKeys.ADAPTER, MentorApplicationConstants.TOPIC_EXPLANATION);
    metadata.putAll(governanceMetadata == null ? Map.of() : governanceMetadata);
    return new AgentRequest(
        null,
        null,
        List.of(LlmMessage.user(prompt)),
        metadata);
  }
}
