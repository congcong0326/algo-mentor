package org.congcong.algomentor.mentor.application;

import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.congcong.algomentor.agent.core.StreamingAgentRunner;
import org.congcong.algomentor.domain.learning.LearningTopic;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.springframework.stereotype.Service;

@Service
public class ExplainTopicUseCase {

  private final AgentRunner agentRunner;
  private final StreamingAgentRunner streamingAgentRunner;

  public ExplainTopicUseCase(AgentRunner agentRunner, StreamingAgentRunner streamingAgentRunner) {
    this.agentRunner = agentRunner;
    this.streamingAgentRunner = streamingAgentRunner;
  }

  public String explain(String topic) {
    return agentRunner.run(new AgentRequest(LearningTopic.of(topic))).content();
  }

  public Flow.Publisher<LlmStreamEvent> stream(String topic) {
    return streamingAgentRunner.stream(new AgentRequest(LearningTopic.of(topic)));
  }
}
