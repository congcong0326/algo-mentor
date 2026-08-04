package org.congcong.algomentor.llm.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffortResolver;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class LlmReasoningEffortTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void roundTripsAllWireValuesWithoutCaseCoercion() throws Exception {
    for (LlmReasoningEffort effort : LlmReasoningEffort.values()) {
      String json = objectMapper.writeValueAsString(effort);

      assertThat(json).isEqualTo('"' + effort.wireValue() + '"');
      assertThat(objectMapper.readValue(json, LlmReasoningEffort.class)).isEqualTo(effort);
    }

    assertThatThrownBy(() -> objectMapper.readValue("\"HIGH\"", LlmReasoningEffort.class))
        .isInstanceOf(Exception.class);
    assertThatThrownBy(() -> objectMapper.readValue("\"unsupported\"", LlmReasoningEffort.class))
        .isInstanceOf(Exception.class);
    assertThatThrownBy(() -> objectMapper.readValue("1", LlmReasoningEffort.class))
        .isInstanceOf(Exception.class);
  }

  @Test
  void resolvesRequestOverrideThenRouteThenProviderDefault() {
    LlmInvocationTarget routeHigh = target(LlmReasoningEffort.HIGH);
    LlmCompletionRequest request = request(LlmGenerationOptions.defaults(), routeHigh);

    assertThat(LlmReasoningEffortResolver.resolve(request)).isEqualTo(LlmReasoningEffort.HIGH);
    assertThat(LlmReasoningEffortResolver.resolve(
        request(LlmGenerationOptions.defaults().withReasoningEffort(LlmReasoningEffort.NONE), routeHigh)))
        .isEqualTo(LlmReasoningEffort.NONE);
    assertThat(LlmReasoningEffortResolver.resolve(
        request(LlmGenerationOptions.defaults(), target(null)))).isNull();
  }

  private static LlmCompletionRequest request(
      LlmGenerationOptions options,
      LlmInvocationTarget invocationTarget
  ) {
    return LlmCompletionRequest.builder()
        .modelSelector(new LlmModelSelector(null, LlmModelId.of("test-model"), Set.of(), "test"))
        .messages(List.of(LlmMessage.user("hello")))
        .options(options)
        .invocationTarget(invocationTarget)
        .build();
  }

  private static LlmInvocationTarget target(LlmReasoningEffort routeEffort) {
    return new LlmInvocationTarget(
        LlmProviderType.of("test"),
        1L,
        1L,
        LlmModelId.of("test-model"),
        Instant.parse("2026-08-03T00:00:00Z"),
        Set.of(LlmCapability.CHAT_COMPLETION),
        new StubClient(),
        routeEffort);
  }

  private static final class StubClient implements LlmProviderClient {

    @Override
    public LlmCompletionResult complete(LlmModelId upstreamModelId, LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmModelId upstreamModelId, LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }
  }
}
