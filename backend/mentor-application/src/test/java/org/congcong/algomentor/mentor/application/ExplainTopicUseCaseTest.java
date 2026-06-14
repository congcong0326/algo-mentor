package org.congcong.algomentor.mentor.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentResponse;
import org.congcong.algomentor.agent.core.AgentRunner;
import org.congcong.algomentor.agent.core.StreamingAgentRunner;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class ExplainTopicUseCaseTest {

  @Test
  void delegatesTopicExplanationToAgentRunner() {
    ExplainTopicUseCase useCase = new ExplainTopicUseCase(new StubAgentRunner(), new StubStreamingAgentRunner());

    String explanation = useCase.explain("binary search");

    assertThat(explanation).isEqualTo("Explain binary search with invariants.");
  }

  @Test
  void delegatesStreamingTopicExplanationToStreamingAgentRunner() {
    StubStreamingAgentRunner streamingRunner = new StubStreamingAgentRunner();
    ExplainTopicUseCase useCase = new ExplainTopicUseCase(new StubAgentRunner(), streamingRunner);

    Flow.Publisher<LlmStreamEvent> publisher = useCase.stream("binary search");

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

  private static final class StubStreamingAgentRunner extends StreamingAgentRunner {

    private final SubmissionPublisher<LlmStreamEvent> publisher = new SubmissionPublisher<>();
    private AgentRequest lastStreamRequest;

    private StubStreamingAgentRunner() {
      super(request -> {
        throw new UnsupportedOperationException("stream stub must override stream");
      });
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(AgentRequest request) {
      this.lastStreamRequest = request;
      return publisher;
    }
  }
}
