package org.congcong.algomentor.llm.openai.compatible;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffortResolver;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleReasoningEffortTest {

  private static final OpenAiCompatibleProviderProfile PROFILE = new TestProfile();
  private static final LlmProviderId PROVIDER_ID = PROFILE.providerId();
  private static final LlmModelId MODEL_ID = LlmModelId.of("gpt-5.2");
  private final OpenAiCompatibleResponsesMapper mapper = new OpenAiCompatibleResponsesMapper(new ObjectMapper(), PROFILE);

  @Test
  void omitsReasoningWhenEffectiveEffortIsNull() {
    ResponseCreateParams params = mapper.toParams(request(null), MODEL_ID);

    assertThat(params.reasoning()).isEmpty();
    assertThat(params._reasoning().isMissing()).isTrue();
    assertThat(serializedBody(params).has("reasoning")).isFalse();
  }

  @Test
  void mapsEveryProtocolEffortToTheExactResponsesWireValue() {
    for (LlmReasoningEffort effort : LlmReasoningEffort.values()) {
      ResponseCreateParams params = mapper.toParams(request(effort), MODEL_ID);

      assertThat(params.reasoning()).hasValueSatisfying(reasoning ->
          assertThat(reasoning.effort()).hasValueSatisfying(sdkEffort ->
              assertThat(sdkEffort.asString()).isEqualTo(effort.wireValue())));
      assertThat(serializedBody(params).path("reasoning").path("effort").asText())
          .isEqualTo(effort.wireValue());
    }
  }

  @Test
  void mapsTheGatewayResolvedRequestInsteadOfReapplyingRoutePrecedence() {
    LlmCompletionRequest routeHigh = request(null).withInvocationTarget(invocationTarget(LlmReasoningEffort.HIGH));
    LlmCompletionRequest requestNone = routeHigh.withOptions(
        LlmGenerationOptions.defaults().withReasoningEffort(LlmReasoningEffort.NONE));

    assertThat(effortOf(mapper.toParams(LlmReasoningEffortResolver.apply(requestNone), MODEL_ID)))
        .isEqualTo("none");
    assertThat(effortOf(mapper.toParams(LlmReasoningEffortResolver.apply(routeHigh), MODEL_ID)))
        .isEqualTo("high");
  }

  @Test
  void usesEquivalentParamsForSynchronousAndStreamingCalls() {
    CapturingResponsesClient responsesClient = new CapturingResponsesClient();
    OpenAiCompatibleProviderClient providerClient = new OpenAiCompatibleProviderClient(responsesClient, PROFILE);
    LlmCompletionRequest request = request(LlmReasoningEffort.MAX);

    assertThatThrownBy(() -> providerClient.complete(MODEL_ID, request))
        .isInstanceOf(LlmException.class);
    providerClient.stream(MODEL_ID, request);

    assertThat(responsesClient.completeParams).isEqualTo(responsesClient.streamParams);
    assertThat(effortOf(responsesClient.completeParams)).isEqualTo("max");
  }

  private static LlmCompletionRequest request(LlmReasoningEffort effort) {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(PROVIDER_ID, MODEL_ID))
        .messages(List.of(LlmMessage.user("hello")))
        .options(LlmGenerationOptions.defaults().withReasoningEffort(effort))
        .build();
  }

  private static LlmInvocationTarget invocationTarget(LlmReasoningEffort effort) {
    return new LlmInvocationTarget(
        PROFILE.providerType(),
        1L,
        1L,
        MODEL_ID,
        Instant.parse("2026-08-03T00:00:00Z"),
        Set.of(LlmCapability.CHAT_COMPLETION, LlmCapability.REASONING_EFFORT),
        new NoopProviderClient(),
        effort);
  }

  private static String effortOf(ResponseCreateParams params) {
    return params.reasoning().orElseThrow().effort().orElseThrow().asString();
  }

  private static JsonNode serializedBody(ResponseCreateParams params) {
    return ObjectMappers.jsonMapper().valueToTree(params._body());
  }

  private static final class CapturingResponsesClient implements OpenAiCompatibleResponsesClient {
    private ResponseCreateParams completeParams;
    private ResponseCreateParams streamParams;

    @Override
    public Response create(ResponseCreateParams params) {
      completeParams = params;
      throw new IllegalStateException("expected test failure after parameter capture");
    }

    @Override
    public StreamResponse<ResponseStreamEvent> createStreaming(ResponseCreateParams params) {
      streamParams = params;
      return new EmptyStreamResponse();
    }
  }

  private static final class EmptyStreamResponse implements StreamResponse<ResponseStreamEvent> {
    @Override
    public Stream<ResponseStreamEvent> stream() {
      return Stream.empty();
    }

    @Override
    public void close() {
    }
  }

  private static final class NoopProviderClient implements LlmProviderClient {
    @Override
    public LlmCompletionResult complete(LlmModelId upstreamModelId, LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.concurrent.Flow.Publisher<LlmStreamEvent> stream(
        LlmModelId upstreamModelId,
        LlmCompletionRequest request
    ) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class TestProfile implements OpenAiCompatibleProviderProfile {
    private static final LlmProviderType PROVIDER_TYPE = LlmProviderType.of("compatible-test");

    @Override
    public LlmProviderType providerType() {
      return PROVIDER_TYPE;
    }

    @Override
    public String displayName() {
      return "Compatible test provider";
    }

    @Override
    public Set<LlmCapability> supportedCapabilities() {
      return Set.of(LlmCapability.CHAT_COMPLETION, LlmCapability.REASONING_EFFORT);
    }

    @Override
    public Set<LlmReasoningEffort> acceptedReasoningEfforts() {
      return Set.of(LlmReasoningEffort.values());
    }

    @Override
    public void validateRequest(LlmModelId modelId, LlmCompletionRequest request) {
    }

    @Override
    public boolean requiresReasoningContinuationForToolCalls() {
      return false;
    }
  }
}
