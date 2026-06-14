package org.congcong.algomentor.mentor.application;

import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.domain.learning.LearningTopic;
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
    return agentRunner.run(new AgentRequest(LearningTopic.of(topic))).content();
  }

  public Flow.Publisher<AgentStreamEvent> stream(String topic) {
    return agentLoopRunner.stream(new AgentRequest(LearningTopic.of(topic)));
  }
}
