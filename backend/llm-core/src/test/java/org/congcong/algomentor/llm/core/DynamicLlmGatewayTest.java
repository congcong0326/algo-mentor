package org.congcong.algomentor.llm.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.gateway.DynamicLlmGateway;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class DynamicLlmGatewayTest {

  private static final LlmProviderType OPENAI = LlmProviderType.of("openai");
  private static final LlmModelId MODEL = LlmModelId.of("gpt-test");

  @Test
  void dispatchesThroughResolvedTargetWithoutDefaultModel() {
    RecordingClient client = new RecordingClient();
    DynamicLlmGateway gateway = new DynamicLlmGateway();

    LlmCompletionResult result = gateway.complete(request(client, Set.of(LlmCapability.CHAT_COMPLETION)));

    assertThat(result.model()).isEqualTo(MODEL);
    assertThat(client.completedWith).isEqualTo(MODEL);
  }

  @Test
  void rejectsMissingTargetWithoutFallingBackToSelector() {
    DynamicLlmGateway gateway = new DynamicLlmGateway();
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(new LlmModelSelector(null, MODEL, Set.of(), null))
        .messages(List.of(LlmMessage.user("hello")))
        .build();

    assertThatThrownBy(() -> gateway.complete(request))
        .isInstanceOfSatisfying(LlmException.class,
            exception -> assertThat(exception.code()).isEqualTo(LlmErrorCode.INVALID_REQUEST));
  }

  @Test
  void checksProviderCapabilitiesBeforeDispatch() {
    RecordingClient client = new RecordingClient();
    DynamicLlmGateway gateway = new DynamicLlmGateway();
    LlmCompletionRequest baseRequest = request(client, Set.of(LlmCapability.CHAT_COMPLETION));
    LlmCompletionRequest request = new LlmCompletionRequest(
        baseRequest.modelSelector(),
        baseRequest.messages(),
        baseRequest.options(),
        baseRequest.tools(),
        baseRequest.toolChoice(),
        new LlmResponseFormat.JsonSchema(
            "response", JsonNodeFactory.instance.objectNode().put("type", "object"), true),
        baseRequest.metadata(),
        baseRequest.invocationTarget());

    assertThatThrownBy(() -> gateway.complete(request))
        .isInstanceOfSatisfying(LlmException.class,
            exception -> assertThat(exception.code()).isEqualTo(LlmErrorCode.UNSUPPORTED_CAPABILITY));
    assertThat(client.completedWith).isNull();
  }

  private static LlmCompletionRequest request(RecordingClient client, Set<LlmCapability> capabilities) {
    return LlmCompletionRequest.builder()
        .modelSelector(new LlmModelSelector(null, null, Set.of(), "test"))
        .messages(List.of(LlmMessage.user("hello")))
        .invocationTarget(target(client, capabilities))
        .build();
  }

  private static LlmInvocationTarget target(RecordingClient client, Set<LlmCapability> capabilities) {
    return new LlmInvocationTarget(
        OPENAI,
        11L,
        17L,
        MODEL,
        Instant.parse("2026-07-27T00:00:00Z"),
        capabilities,
        client);
  }

  private static final class RecordingClient implements LlmProviderClient {

    private LlmModelId completedWith;

    @Override
    public LlmCompletionResult complete(LlmModelId upstreamModelId, LlmCompletionRequest request) {
      completedWith = upstreamModelId;
      return new LlmCompletionResult(
          LlmMessage.assistant("ok"),
          List.of(),
          JsonNodeFactory.instance.nullNode(),
          LlmFinishReason.STOP,
          LlmUsage.empty(),
          LlmProviderId.of(OPENAI.value()),
          upstreamModelId,
          Map.of());
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmModelId upstreamModelId, LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }
  }
}
