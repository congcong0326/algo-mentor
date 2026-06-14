package org.congcong.algomentor.agent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.congcong.algomentor.domain.learning.LearningTopic;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.junit.jupiter.api.Test;

class AgentRunnerTest {

  @Test
  void runsTopicExplanationThroughLlmGatewayContract() {
    FakeGateway gateway = new FakeGateway();
    AgentRunner runner = new AgentRunner(gateway, "gpt-test");

    AgentResponse response = runner.run(new AgentRequest(LearningTopic.of("binary search")));

    assertThat(response.content()).isEqualTo("Binary search explanation");
    assertThat(gateway.lastRequest.messages().get(0).text())
        .contains("binary search");
    assertThat(gateway.lastRequest.modelSelector().modelId())
        .hasValue(LlmModelId.of("gpt-test"));
    assertThat(gateway.lastRequest.modelSelector().purpose())
        .isEqualTo("topic-explanation");
  }

  private static final class FakeGateway implements LlmGateway {

    private LlmCompletionRequest lastRequest;

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      this.lastRequest = request;
      return new LlmCompletionResult(
          LlmMessage.assistant("Binary search explanation"),
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
      throw new UnsupportedOperationException("Streaming is not used by this test");
    }
  }
}
