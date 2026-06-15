package org.congcong.algomentor.agent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.congcong.algomentor.domain.learning.LearningTopic;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class StreamingAgentRunnerTest {

  @Test
  void streamsTopicExplanationThroughLlmGatewayContract() {
    FakeGateway gateway = new FakeGateway();
    StreamingAgentRunner runner = new StreamingAgentRunner(
        gateway,
        new LlmModelSelector(null, LlmModelId.of("gpt-test"), Set.of(), null));

    Flow.Publisher<LlmStreamEvent> publisher = runner.stream(new AgentRequest(LearningTopic.of("two pointers")));

    assertThat(publisher).isSameAs(gateway.streamPublisher);
    assertThat(gateway.lastStreamRequest.messages().get(0).text())
        .contains("two pointers");
    assertThat(gateway.lastStreamRequest.modelSelector().modelId())
        .hasValue(LlmModelId.of("gpt-test"));
    assertThat(gateway.lastStreamRequest.modelSelector().purpose())
        .isEqualTo("topic-explanation");
  }

  @Test
  void preservesConfiguredProviderAndModelSelector() {
    FakeGateway gateway = new FakeGateway();
    StreamingAgentRunner runner = new StreamingAgentRunner(
        gateway,
        new LlmModelSelector(
            LlmProviderId.of("test-provider"),
            LlmModelId.of("test-model"),
            Set.of(),
            null));

    runner.stream(new AgentRequest(LearningTopic.of("two pointers")));

    assertThat(gateway.lastStreamRequest.modelSelector().providerId())
        .hasValue(LlmProviderId.of("test-provider"));
    assertThat(gateway.lastStreamRequest.modelSelector().modelId())
        .hasValue(LlmModelId.of("test-model"));
    assertThat(gateway.lastStreamRequest.modelSelector().purpose())
        .isEqualTo("topic-explanation");
  }

  private static final class FakeGateway implements LlmGateway {

    private LlmCompletionRequest lastStreamRequest;
    private final SubmissionPublisher<LlmStreamEvent> streamPublisher = new SubmissionPublisher<>();

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      return new LlmCompletionResult(
          LlmMessage.assistant("unused"),
          List.of(),
          null,
          LlmFinishReason.STOP,
          LlmUsage.empty(),
          LlmProviderId.of("test"),
          LlmModelId.of("gpt-test"),
          Map.of());
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      this.lastStreamRequest = request;
      return streamPublisher;
    }
  }
}
