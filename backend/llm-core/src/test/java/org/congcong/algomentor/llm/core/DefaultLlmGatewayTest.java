package org.congcong.algomentor.llm.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class DefaultLlmGatewayTest {

  private static final LlmProviderId OPENAI = LlmProviderId.of("openai");
  private static final LlmModelId GPT_5_2 = LlmModelId.of("gpt-5.2");

  @Test
  void routesRequestToSelectedProvider() {
    FakeProvider provider = new FakeProvider(OPENAI, Set.of(LlmCapability.CHAT_COMPLETION, LlmCapability.TOKEN_USAGE));
    DefaultLlmGateway gateway = new DefaultLlmGateway(List.of(provider), OPENAI, GPT_5_2);
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(OPENAI, GPT_5_2))
        .messages(List.of(LlmMessage.user("hello")))
        .build();

    LlmCompletionResult result = gateway.complete(request);

    assertThat(result.message().text()).isEqualTo("ok");
    assertThat(provider.callCount()).isOne();
    assertThat(provider.lastRequest().modelSelector().providerId()).contains(OPENAI);
    assertThat(provider.lastRequest().modelSelector().modelId()).contains(GPT_5_2);
  }

  @Test
  void rejectsUnsupportedToolCallingCapabilityBeforeProviderCall() {
    FakeProvider provider = new FakeProvider(OPENAI, Set.of(LlmCapability.CHAT_COMPLETION));
    DefaultLlmGateway gateway = new DefaultLlmGateway(List.of(provider), OPENAI, GPT_5_2);
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(OPENAI, GPT_5_2))
        .messages(List.of(LlmMessage.user("hello")))
        .tools(List.of(new LlmToolSpec(
            "search_problem",
            "Search an algorithm problem",
            JsonNodeFactory.instance.objectNode().put("type", "object"),
            true)))
        .build();

    assertThatThrownBy(() -> gateway.complete(request))
        .isInstanceOf(LlmException.class)
        .extracting("code")
        .isEqualTo(LlmErrorCode.UNSUPPORTED_CAPABILITY);
    assertThat(provider.callCount()).isZero();
  }

  private static final class FakeProvider implements LlmProvider {
    private final LlmProviderId id;
    private final LlmProviderCapabilities capabilities;
    private int callCount;
    private LlmCompletionRequest lastRequest;

    private FakeProvider(LlmProviderId id, Set<LlmCapability> supportedCapabilities) {
      this.id = id;
      this.capabilities = new LlmProviderCapabilities(
          supportedCapabilities,
          Map.of(GPT_5_2.value(), new LlmModelDescriptor(
              id,
              GPT_5_2,
              "GPT 5.2",
              supportedCapabilities,
              128000,
              8192,
              LlmGenerationOptions.defaults(),
              Map.of())));
    }

    @Override
    public LlmProviderId id() {
      return id;
    }

    @Override
    public LlmProviderCapabilities capabilities() {
      return capabilities;
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      callCount++;
      lastRequest = request;
      return new LlmCompletionResult(
          LlmMessage.assistant("ok"),
          List.of(),
          null,
          LlmFinishReason.STOP,
          LlmUsage.empty(),
          id,
          request.modelSelector().modelId().orElseThrow(),
          Map.of());
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("stream not implemented");
    }

    private int callCount() {
      return callCount;
    }

    private LlmCompletionRequest lastRequest() {
      return lastRequest;
    }
  }
}
