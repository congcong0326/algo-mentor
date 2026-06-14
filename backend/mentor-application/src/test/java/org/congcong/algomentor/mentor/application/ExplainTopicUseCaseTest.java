package org.congcong.algomentor.mentor.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentResponse;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.junit.jupiter.api.Test;

class ExplainTopicUseCaseTest {

  @Test
  void delegatesTopicExplanationToAgentRunner() {
    ExplainTopicUseCase useCase = new ExplainTopicUseCase(new StubAgentRunner());

    String explanation = useCase.explain("binary search");

    assertThat(explanation).isEqualTo("Explain binary search with invariants.");
  }

  private static final class StubAgentRunner extends AgentRunner {

    private StubAgentRunner() {
      super(request -> new AgentResponse("unused"));
    }

    @Override
    public AgentResponse run(AgentRequest request) {
      return new AgentResponse("Explain " + request.topic().title() + " with invariants.");
    }
  }
}
