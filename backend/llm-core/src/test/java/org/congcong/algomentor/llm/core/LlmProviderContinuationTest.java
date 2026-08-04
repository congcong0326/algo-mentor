package org.congcong.algomentor.llm.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderContinuation;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.junit.jupiter.api.Test;

class LlmProviderContinuationTest {

  private static final String SENTINEL = "continuation-secret-sentinel";
  private static final LlmProviderType PROVIDER_TYPE = LlmProviderType.of("compatible-test");

  @Test
  void validatesPayloadDefensivelyCopiesItAndRedactsToString() {
    var source = JsonNodeFactory.instance.objectNode().put("secret", SENTINEL);
    LlmProviderContinuation continuation = new LlmProviderContinuation(PROVIDER_TYPE, source);
    source.put("secret", "mutated");
    var accessorCopy = (com.fasterxml.jackson.databind.node.ObjectNode) continuation.payload();
    accessorCopy.put("secret", "mutated-again");

    assertThat(continuation.providerType()).isEqualTo(PROVIDER_TYPE);
    assertThat(continuation.payload().path("secret").asText()).isEqualTo(SENTINEL);
    assertThat(continuation.toString()).isEqualTo("LlmProviderContinuation[REDACTED]");
    assertThatThrownBy(() -> new LlmProviderContinuation(PROVIDER_TYPE, JsonNodeFactory.instance.textNode("invalid")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LlmProviderContinuation(null, JsonNodeFactory.instance.objectNode()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void excludesContinuationFromAllCoreCarrierJson() throws Exception {
    LlmProviderContinuation continuation = new LlmProviderContinuation(
        PROVIDER_TYPE,
        JsonNodeFactory.instance.objectNode().put("secret", SENTINEL));
    LlmToolCall toolCall = new LlmToolCall("call_1", "lookup", JsonNodeFactory.instance.objectNode());
    LlmMessage message = LlmMessage.assistantToolCalls(List.of(toolCall), continuation);
    LlmCompletionResult result = new LlmCompletionResult(
        message,
        List.of(toolCall),
        null,
        LlmFinishReason.TOOL_CALLS,
        LlmUsage.empty(),
        LlmProviderId.of(PROVIDER_TYPE.value()),
        LlmModelId.of("test-model"),
        Map.of(),
        continuation);
    LlmStreamEvent.MessageEnd end = new LlmStreamEvent.MessageEnd(
        LlmFinishReason.TOOL_CALLS,
        Map.of(),
        continuation);

    String json = new ObjectMapper().writeValueAsString(Map.of("message", message, "result", result, "end", end));

    assertThat(json).doesNotContain(SENTINEL, "providerContinuation", "payload");
    assertThat(new LlmStreamEvent.MessageEnd(null, null).providerContinuation()).isNull();
    assertThat(new LlmCompletionResult(
        LlmMessage.assistant("done"),
        List.of(),
        null,
        LlmFinishReason.STOP,
        LlmUsage.empty(),
        LlmProviderId.of(PROVIDER_TYPE.value()),
        LlmModelId.of("test-model"),
        Map.of()).providerContinuation()).isNull();
  }
}
