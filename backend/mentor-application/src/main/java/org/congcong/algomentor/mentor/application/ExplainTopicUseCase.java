package org.congcong.algomentor.mentor.application;

import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.domain.learning.LearningTopic;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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
    return agentLoopRunner.stream(toAgentRequest(LearningTopic.of(topic)));
  }

  private AgentRequest toAgentRequest(LearningTopic topic) {
    String prompt = "Explain the learning topic for an algorithm student: " + topic.title();
    return new AgentRequest(
        null,
        null,
        List.of(LlmMessage.user(prompt)),
        Map.of(
            AgentRuntimeMetadataKeys.TITLE, topic.title(),
            AgentRuntimeMetadataKeys.TOPIC_TITLE, topic.title(),
            AgentRuntimeMetadataKeys.ADAPTER, MentorApplicationConstants.TOPIC_EXPLANATION));
  }
}
