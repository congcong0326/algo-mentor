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
    assertThat(provider.models()).hasSize(1);
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

  @Test
  void rejectsUnsupportedJsonSchemaCapabilityBeforeProviderCall() {
    FakeProvider provider = new FakeProvider(OPENAI, Set.of(LlmCapability.CHAT_COMPLETION));
    DefaultLlmGateway gateway = new DefaultLlmGateway(List.of(provider), OPENAI, GPT_5_2);
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(OPENAI, GPT_5_2))
        .messages(List.of(LlmMessage.user("hello")))
        .responseFormat(new LlmResponseFormat.JsonSchema(
            "answer",
            JsonNodeFactory.instance.objectNode().put("type", "object"),
            true))
        .build();

    assertThatThrownBy(() -> gateway.complete(request))
        .isInstanceOf(LlmException.class)
        .extracting("code")
        .isEqualTo(LlmErrorCode.UNSUPPORTED_CAPABILITY);
    assertThat(provider.callCount()).isZero();
  }

  @Test
  void rejectsUnsupportedVisionCapabilityBeforeProviderCall() {
    FakeProvider provider = new FakeProvider(OPENAI, Set.of(LlmCapability.CHAT_COMPLETION));
    DefaultLlmGateway gateway = new DefaultLlmGateway(List.of(provider), OPENAI, GPT_5_2);
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(OPENAI, GPT_5_2))
        .messages(List.of(new LlmMessage(
            LlmMessage.Role.USER,
            List.of(new LlmContentPart.Image("https://example.com/a.png", null, "image/png")),
            null,
            null,
            Map.of())))
        .build();

    assertThatThrownBy(() -> gateway.complete(request))
        .isInstanceOf(LlmException.class)
        .extracting("code")
        .isEqualTo(LlmErrorCode.UNSUPPORTED_CAPABILITY);
    assertThat(provider.callCount()).isZero();
  }

  @Test
  void rejectsUnsupportedFileCapabilityBeforeProviderCall() {
    FakeProvider provider = new FakeProvider(OPENAI, Set.of(LlmCapability.CHAT_COMPLETION));
    DefaultLlmGateway gateway = new DefaultLlmGateway(List.of(provider), OPENAI, GPT_5_2);
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(OPENAI, GPT_5_2))
        .messages(List.of(new LlmMessage(
            LlmMessage.Role.USER,
            List.of(new LlmContentPart.File("file-1", "notes.txt", "text/plain")),
            null,
            null,
            Map.of())))
        .build();

    assertThatThrownBy(() -> gateway.complete(request))
        .isInstanceOf(LlmException.class)
        .extracting("code")
        .isEqualTo(LlmErrorCode.UNSUPPORTED_CAPABILITY);
    assertThat(provider.callCount()).isZero();
  }

  @Test
  void rejectsUnsupportedStreamingCapabilityBeforeProviderCall() {
    FakeProvider provider = new FakeProvider(OPENAI, Set.of(LlmCapability.CHAT_COMPLETION));
    DefaultLlmGateway gateway = new DefaultLlmGateway(List.of(provider), OPENAI, GPT_5_2);
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(OPENAI, GPT_5_2))
        .messages(List.of(LlmMessage.user("hello")))
        .build();

    assertThatThrownBy(() -> gateway.stream(request))
        .isInstanceOf(LlmException.class)
        .extracting("code")
        .isEqualTo(LlmErrorCode.UNSUPPORTED_CAPABILITY);
    assertThat(provider.streamCount()).isZero();
  }

  @Test
  void rejectsUnknownDefaultProviderAtConstruction() {
    LlmProviderId unknownProvider = LlmProviderId.of("missing");
    FakeProvider provider = new FakeProvider(OPENAI, Set.of(LlmCapability.CHAT_COMPLETION));

    assertThatThrownBy(() -> new DefaultLlmGateway(List.of(provider), unknownProvider, GPT_5_2))
        .isInstanceOfSatisfying(LlmException.class, exception -> {
          assertThat(exception.code()).isEqualTo(LlmErrorCode.INVALID_REQUEST);
          assertThat(exception.provider()).isEqualTo(unknownProvider);
          assertThat(exception.model()).isEqualTo(GPT_5_2);
          assertThat(exception).hasMessage("Unknown default LLM provider: missing");
        });
  }

  @Test
  void rejectsUnknownDefaultModelAtConstruction() {
    LlmModelId unknownModel = LlmModelId.of("gpt-missing");
    FakeProvider provider = new FakeProvider(OPENAI, Set.of(LlmCapability.CHAT_COMPLETION));

    assertThatThrownBy(() -> new DefaultLlmGateway(List.of(provider), OPENAI, unknownModel))
        .isInstanceOfSatisfying(LlmException.class, exception -> {
          assertThat(exception.code()).isEqualTo(LlmErrorCode.INVALID_REQUEST);
          assertThat(exception.provider()).isEqualTo(OPENAI);
          assertThat(exception.model()).isEqualTo(unknownModel);
          assertThat(exception).hasMessage("Unknown default LLM model: gpt-missing");
        });
  }

  @Test
  void rejectsUnknownRequestedProviderWithContext() {
    LlmProviderId unknownProvider = LlmProviderId.of("missing");
    FakeProvider provider = new FakeProvider(OPENAI, Set.of(LlmCapability.CHAT_COMPLETION));
    DefaultLlmGateway gateway = new DefaultLlmGateway(List.of(provider), OPENAI, GPT_5_2);
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(unknownProvider, GPT_5_2))
        .messages(List.of(LlmMessage.user("hello")))
        .build();

    assertThatThrownBy(() -> gateway.complete(request))
        .isInstanceOfSatisfying(LlmException.class, exception -> {
          assertThat(exception.code()).isEqualTo(LlmErrorCode.INVALID_REQUEST);
          assertThat(exception.provider()).isEqualTo(unknownProvider);
          assertThat(exception.model()).isEqualTo(GPT_5_2);
          assertThat(exception).hasMessage("Unknown LLM provider: missing");
        });
  }

  @Test
  void rejectsUnknownRequestedModelWithContext() {
    LlmModelId unknownModel = LlmModelId.of("gpt-missing");
    FakeProvider provider = new FakeProvider(OPENAI, Set.of(LlmCapability.CHAT_COMPLETION));
    DefaultLlmGateway gateway = new DefaultLlmGateway(List.of(provider), OPENAI, GPT_5_2);
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(OPENAI, unknownModel))
        .messages(List.of(LlmMessage.user("hello")))
        .build();

    assertThatThrownBy(() -> gateway.complete(request))
        .isInstanceOfSatisfying(LlmException.class, exception -> {
          assertThat(exception.code()).isEqualTo(LlmErrorCode.INVALID_REQUEST);
          assertThat(exception.provider()).isEqualTo(OPENAI);
          assertThat(exception.model()).isEqualTo(unknownModel);
          assertThat(exception).hasMessage("Unknown LLM model: gpt-missing");
        });
  }

  @Test
  void streamEventsValidateAndDefaultFields() {
    assertThatThrownBy(() -> new LlmStreamEvent.ContentDelta(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM stream content delta must not be null");

    LlmStreamEvent.ToolCallDelta partialDelta = new LlmStreamEvent.ToolCallDelta("call-1", "{\"a");
    assertThat(partialDelta.argumentsDelta()).isEqualTo("{\"a");

    assertThatThrownBy(() -> new LlmStreamEvent.ToolCallDelta(" ", "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM stream tool call id must not be blank");

    assertThatThrownBy(() -> new LlmStreamEvent.ToolCallDelta("call-1", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM stream tool call arguments delta must not be null");

    LlmStreamEvent.MessageEnd messageEnd = new LlmStreamEvent.MessageEnd(null, null);
    assertThat(messageEnd.finishReason()).isEqualTo(LlmFinishReason.UNKNOWN);
    assertThat(messageEnd.metadata()).isEmpty();

    assertThatThrownBy(() -> new LlmStreamEvent.Error(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM stream error must not be null");
  }

  @Test
  void llmExceptionCarriesContext() {
    RuntimeException cause = new RuntimeException("transport failed");
    Map<String, Object> metadata = Map.of("requestId", "req-123");

    LlmException exception = new LlmException(
        LlmErrorCode.RATE_LIMITED,
        "rate limited",
        OPENAI,
        GPT_5_2,
        true,
        metadata,
        cause);

    assertThat(exception.code()).isEqualTo(LlmErrorCode.RATE_LIMITED);
    assertThat(exception.getCode()).isEqualTo(LlmErrorCode.RATE_LIMITED);
    assertThat(exception.provider()).isEqualTo(OPENAI);
    assertThat(exception.model()).isEqualTo(GPT_5_2);
    assertThat(exception.retryable()).isTrue();
    assertThat(exception.metadata()).containsEntry("requestId", "req-123");
    assertThat(exception.getCause()).isSameAs(cause);
    assertThatThrownBy(() -> exception.metadata().put("other", "value"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static final class FakeProvider implements LlmProvider {
    private final LlmProviderId id;
    private final LlmProviderCapabilities capabilities;
    private int callCount;
    private int streamCount;
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
    public List<LlmModelDescriptor> models() {
      return List.copyOf(capabilities.models().values());
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
      streamCount++;
      throw new UnsupportedOperationException("stream not implemented");
    }

    private int callCount() {
      return callCount;
    }

    private LlmCompletionRequest lastRequest() {
      return lastRequest;
    }

    private int streamCount() {
      return streamCount;
    }
  }
}
