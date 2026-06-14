package org.congcong.algomentor.mentor.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentResponse;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class ExplainTopicUseCaseTest {

  @Test
  void delegatesTopicExplanationToAgentRunner() {
    ExplainTopicUseCase useCase = new ExplainTopicUseCase(new StubAgentRunner(), new StubAgentLoopRunner());

    String explanation = useCase.explain("binary search");

    assertThat(explanation).isEqualTo("Explain binary search with invariants.");
  }

  @Test
  void delegatesStreamingTopicExplanationToAgentLoopRunner() {
    StubAgentLoopRunner streamingRunner = new StubAgentLoopRunner();
    ExplainTopicUseCase useCase = new ExplainTopicUseCase(new StubAgentRunner(), streamingRunner);

    Flow.Publisher<AgentStreamEvent> publisher = useCase.stream("binary search");

    assertThat(publisher).isSameAs(streamingRunner.publisher);
    assertThat(streamingRunner.lastStreamRequest.topic().title()).isEqualTo("binary search");
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

  private static final class StubAgentLoopRunner extends AgentLoopRunner {

    private final SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
    private AgentRequest lastStreamRequest;

    private StubAgentLoopRunner() {
      super(new UnusedGateway(), "gpt-test", AgentToolRegistry.empty(), 1);
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentRequest request) {
      this.lastStreamRequest = request;
      return publisher;
    }
  }

  private static final class UnusedGateway implements LlmGateway {

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("gateway stub must not be called");
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("gateway stub must not be called");
    }
  }
}
