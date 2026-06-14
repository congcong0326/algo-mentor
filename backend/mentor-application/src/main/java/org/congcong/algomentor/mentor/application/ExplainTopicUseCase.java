package org.congcong.algomentor.mentor.application;

import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.congcong.algomentor.domain.learning.LearningTopic;
import org.springframework.stereotype.Service;

@Service
public class ExplainTopicUseCase {

  private final AgentRunner agentRunner;

  public ExplainTopicUseCase(AgentRunner agentRunner) {
    this.agentRunner = agentRunner;
  }

  public String explain(String topic) {
    return agentRunner.run(new AgentRequest(LearningTopic.of(topic))).content();
  }
}
