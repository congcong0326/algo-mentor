package org.congcong.algomentor.llm.deepseek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.openai.core.JsonField;
import com.openai.core.JsonValue;
import com.openai.core.ObjectMappers;
import com.openai.core.http.Headers;
import com.openai.core.http.StreamResponse;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.openai.models.ErrorObject;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseCreatedEvent;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseReasoningItem;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.ResponseTextDeltaEvent;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.ToolChoiceOptions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderInstanceSpec;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.openai.compatible.OpenAiCompatibleResponsesClient;
import org.junit.jupiter.api.Test;

class DeepSeekProviderClientTest {

  private static final LlmModelId MODEL_ID = LlmModelId.of("deepseek-fixture-model");

  @Test
  void mapsTextStructuredOutputUsageAndProviderIdentity() {
    FakeClient client = new FakeClient(response("{\"answer\":42}"));
    LlmProviderClient provider = adapter(client).createClient(instance());

    LlmCompletionResult result = provider.complete(MODEL_ID, request(List.of(LlmMessage.user("return JSON")), LlmReasoningEffort.HIGH));

    assertThat(result.provider().value()).isEqualTo("deepseek");
    assertThat(result.model()).isEqualTo(MODEL_ID);
    assertThat(result.message().text()).isEqualTo("{\"answer\":42}");
    assertThat(result.structuredOutput().path("answer").asInt()).isEqualTo(42);
    assertThat(result.usage()).extracting("inputTokens", "outputTokens", "cachedTokens", "reasoningTokens", "totalTokens")
        .containsExactly(3, 4, 1, 2, 7);
    assertThat(client.params.reasoning()).hasValueSatisfying(value ->
        assertThat(value.effort().orElseThrow().asString()).isEqualTo("high"));
  }

  @Test
  void mapsAllAcceptedEffortsAndKeepsUnsupportedFieldsOutOfDeepSeekRequests() {
    FakeClient client = new FakeClient(response("one"), response("two"), response("three"), response("four"), response("five"));
    LlmProviderClient provider = adapter(client).createClient(instance());
    List<LlmReasoningEffort> efforts = java.util.Arrays.asList(
        null,
        LlmReasoningEffort.NONE,
        LlmReasoningEffort.LOW,
        LlmReasoningEffort.HIGH,
        LlmReasoningEffort.MAX);

    for (LlmReasoningEffort effort : efforts) {
      provider.complete(MODEL_ID, request(List.of(LlmMessage.user("hello")), effort));
    }

    for (int index = 0; index < efforts.size(); index++) {
      LlmReasoningEffort effort = efforts.get(index);
      var params = client.allParams.get(index);
      if (effort == null) {
        assertThat(params.reasoning()).isEmpty();
      } else {
        assertThat(params.reasoning()).hasValueSatisfying(reasoning ->
            assertThat(reasoning.effort().orElseThrow().asString()).isEqualTo(effort.wireValue()));
      }
      com.fasterxml.jackson.databind.JsonNode body = ObjectMappers.jsonMapper().valueToTree(params._body());
      if (effort == null) {
        assertThat(body.fieldNames()).toIterable().containsOnly("model", "input");
      } else {
        assertThat(body.fieldNames()).toIterable().containsOnly("model", "input", "reasoning");
      }
      assertThat(body.toString()).doesNotContain(
          "store", "previous_response_id", "conversation", "metadata", "include", "service_tier", "stop", "seed");
    }
  }

  @Test
  void mapsDeepSeekJsonObjectAndJsonSchemaFormats() {
    FakeClient client = new FakeClient(response("{\"answer\":42}"), response("{\"answer\":42}"));
    LlmProviderClient provider = adapter(client).createClient(instance());

    LlmCompletionResult jsonObject = provider.complete(MODEL_ID, request(
        List.of(LlmMessage.user("return JSON")),
        LlmReasoningEffort.NONE,
        new LlmResponseFormat.JsonObject()));
    ResponseTextConfig objectConfig = client.allParams.get(0).text().orElseThrow();
    assertThat(objectConfig.format().orElseThrow().asJsonObject()).isInstanceOf(com.openai.models.ResponseFormatJsonObject.class);
    assertThat(jsonObject.structuredOutput().path("answer").asInt()).isEqualTo(42);

    provider.complete(MODEL_ID, request(
        List.of(LlmMessage.user("return schema JSON")),
        LlmReasoningEffort.NONE,
        new LlmResponseFormat.JsonSchema(
            "answer",
            JsonNodeFactory.instance.objectNode().put("type", "object"),
            true)));
    ResponseFormatTextJsonSchemaConfig schema = client.allParams.get(1).text().orElseThrow()
        .format().orElseThrow().asJsonSchema();
    assertThat(schema.name()).isEqualTo("answer");
    assertThat(schema.strict()).contains(true);
  }

  @Test
  void restoresDeepSeekContinuationBeforeFunctionResult() {
    FakeClient client = new FakeClient(toolResponse("call_1", "deepseek-continuation-sentinel"), response("done"));
    LlmProviderClient provider = adapter(client).createClient(instance());

    LlmCompletionResult first = provider.complete(MODEL_ID, request(List.of(LlmMessage.user("calculate")), LlmReasoningEffort.HIGH));
    LlmCompletionResult second = provider.complete(MODEL_ID, request(List.of(
        LlmMessage.user("calculate"),
        LlmMessage.assistantToolCalls(first.toolCalls(), first.providerContinuation()),
        LlmMessage.toolResult("call_1", JsonNodeFactory.instance.objectNode().put("value", 42))), LlmReasoningEffort.HIGH));

    assertThat(first.finishReason()).isEqualTo(LlmFinishReason.TOOL_CALLS);
    assertThat(first.providerContinuation().providerType()).isEqualTo(DeepSeekProviderAdapter.PROVIDER_TYPE);
    List<ResponseInputItem> input = client.allParams.get(1).input().orElseThrow().asResponse().stream().toList();
    assertThat(input.get(1).asReasoning().encryptedContent()).contains("deepseek-continuation-sentinel");
    assertThat(input.get(2).asFunctionCall().callId()).isEqualTo("call_1");
    assertThat(input.get(3).asFunctionCallOutput().callId()).isEqualTo("call_1");
    assertThat(second.provider().value()).isEqualTo("deepseek");
  }

  @Test
  void rejectsUnsupportedEffortBeforeCallingTheSdk() {
    FakeClient client = new FakeClient(response("unused"));
    LlmProviderClient provider = adapter(client).createClient(instance());

    assertThatThrownBy(() -> provider.complete(MODEL_ID, request(List.of(LlmMessage.user("hello")), LlmReasoningEffort.MINIMAL)))
        .isInstanceOf(LlmException.class)
        .extracting("code")
        .isEqualTo(LlmErrorCode.INVALID_REQUEST);
    assertThat(client.calls.get()).isZero();
  }

  @Test
  void mapsSdkFailuresToDeepSeekIdentityWithoutOpenAiWording() {
    record ExpectedFailure(RuntimeException source, LlmErrorCode code, boolean retryable) {
    }
    List<ExpectedFailure> failures = List.of(
        new ExpectedFailure(httpError(401), LlmErrorCode.AUTHENTICATION_FAILED, false),
        new ExpectedFailure(httpError(403), LlmErrorCode.PERMISSION_DENIED, false),
        new ExpectedFailure(httpError(408), LlmErrorCode.TIMEOUT, true),
        new ExpectedFailure(httpError(429), LlmErrorCode.RATE_LIMITED, true),
        new ExpectedFailure(httpError(503), LlmErrorCode.PROVIDER_UNAVAILABLE, true),
        new ExpectedFailure(httpError(400), LlmErrorCode.INVALID_REQUEST, false),
        new ExpectedFailure(new OpenAIIoException("transport diagnostic"), LlmErrorCode.PROVIDER_UNAVAILABLE, true));

    for (ExpectedFailure expected : failures) {
      FakeClient client = new FakeClient(response("unused"));
      client.failure = expected.source();
      LlmProviderClient provider = adapter(client).createClient(instance());

      assertThatThrownBy(() -> provider.complete(MODEL_ID, request(List.of(LlmMessage.user("hello")), LlmReasoningEffort.NONE)))
          .isInstanceOfSatisfying(LlmException.class, error -> {
            assertThat(error.code()).isEqualTo(expected.code());
            assertThat(error.retryable()).isEqualTo(expected.retryable());
            assertThat(error.provider().value()).isEqualTo("deepseek");
            assertThat(error.model()).isEqualTo(MODEL_ID);
            assertThat(error.metadata()).containsEntry("provider", "deepseek");
            assertThat(error.getMessage().toLowerCase(java.util.Locale.ROOT)).doesNotContain("openai");
          });
      assertThat(client.calls).hasValue(1);
    }
  }

  @Test
  void streamsTextAndClosesTheFakeSdkResourceOnCompletion() throws Exception {
    FakeClient client = new FakeClient(response("done"));
    client.stream = new FakeStreamResponse(List.of(
        ResponseStreamEvent.ofCreated(ResponseCreatedEvent.builder()
            .response(response(""))
            .sequenceNumber(1)
            .build()),
        ResponseStreamEvent.ofOutputTextDelta(ResponseTextDeltaEvent.builder()
            .contentIndex(0).delta("hel").itemId("msg_1").logprobs(List.of()).outputIndex(0).sequenceNumber(2).build()),
        ResponseStreamEvent.ofCompleted(ResponseCompletedEvent.builder().response(response("hello")).sequenceNumber(3).build())));
    LlmProviderClient provider = adapter(client).createClient(instance());
    CollectingSubscriber subscriber = new CollectingSubscriber();

    provider.stream(MODEL_ID, request(List.of(LlmMessage.user("hello")), LlmReasoningEffort.NONE)).subscribe(subscriber);

    assertThat(subscriber.finished.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(subscriber.events).extracting(Object::getClass).containsExactly(
        LlmStreamEvent.MessageStart.class,
        LlmStreamEvent.ContentDelta.class,
        LlmStreamEvent.Usage.class,
        LlmStreamEvent.MessageEnd.class);
    assertThat(subscriber.events).contains(new LlmStreamEvent.ContentDelta("hel"));
    assertThat(client.stream.closed).isTrue();
  }

  @Test
  void cancellationClosesTheFakeSdkResourceWithoutPublishingContinuation() {
    FakeClient client = new FakeClient(response("unused"));
    client.stream = new FakeStreamResponse(List.of(
        ResponseStreamEvent.ofOutputTextDelta(ResponseTextDeltaEvent.builder()
            .contentIndex(0).delta("partial").itemId("msg_1").logprobs(List.of()).outputIndex(0).sequenceNumber(1).build()),
        ResponseStreamEvent.ofCompleted(ResponseCompletedEvent.builder().response(response("never-reached")).sequenceNumber(2).build())));
    LlmProviderClient provider = adapter(client).createClient(instance());
    CancellingSubscriber subscriber = new CancellingSubscriber();

    provider.stream(MODEL_ID, request(List.of(LlmMessage.user("hello")), LlmReasoningEffort.NONE)).subscribe(subscriber);

    assertThat(client.stream.closed).isTrue();
    assertThat(subscriber.events).contains(new LlmStreamEvent.ContentDelta("partial"));
    assertThat(subscriber.events).noneMatch(LlmStreamEvent.MessageEnd.class::isInstance);
  }

  private static DeepSeekProviderAdapter adapter(FakeClient client) {
    return new DeepSeekProviderAdapter(config -> client);
  }

  private static LlmProviderInstanceSpec instance() {
    return new LlmProviderInstanceSpec(1L, DeepSeekProviderAdapter.PROVIDER_TYPE, JsonNodeFactory.instance.objectNode()
        .put("apiKey", "fixture-key").put("baseUrl", "https://fixture.invalid").put("timeoutSeconds", 30).put("maxRetries", 0),
        Instant.parse("2026-08-03T00:00:00Z"));
  }

  private static LlmCompletionRequest request(List<LlmMessage> messages, LlmReasoningEffort effort) {
    return request(messages, effort, new LlmResponseFormat.Text());
  }

  private static LlmCompletionRequest request(
      List<LlmMessage> messages,
      LlmReasoningEffort effort,
      LlmResponseFormat responseFormat
  ) {
    return LlmCompletionRequest.builder().modelSelector(LlmModelSelector.of(DeepSeekProviderProfile.INSTANCE.providerId(), MODEL_ID))
        .messages(messages).options(LlmGenerationOptions.defaults().withReasoningEffort(effort)).responseFormat(responseFormat).build();
  }

  private static Response toolResponse(String callId, String encryptedContent) {
    return response("").toBuilder().output(List.of(
        com.openai.models.responses.ResponseOutputItem.ofReasoning(reasoning(encryptedContent)),
        com.openai.models.responses.ResponseOutputItem.ofFunctionCall(functionCall(callId)))).build();
  }

  private static Response response(String text) {
    return Response.builder().id("resp_1").createdAt(1.0).error(Optional.empty()).incompleteDetails(Optional.empty())
        .instructions(Optional.empty()).metadata(Optional.empty()).model(MODEL_ID.value()).parallelToolCalls(false)
        .toolChoice(ToolChoiceOptions.AUTO).tools(List.of()).temperature(Optional.empty()).topP(Optional.empty())
        .background(Optional.empty()).completedAt(Optional.empty()).conversation(Optional.empty()).maxOutputTokens(Optional.empty())
        .maxToolCalls(Optional.empty()).moderation(Optional.empty()).previousResponseId(Optional.empty()).prompt(Optional.empty())
        .promptCacheRetention(Optional.empty()).reasoning(Optional.empty()).serviceTier(Optional.empty()).status(ResponseStatus.COMPLETED)
        .text(JsonField.ofNullable(null)).topLogprobs(Optional.empty()).truncation(Optional.empty())
        .usage(ResponseUsage.builder().inputTokens(3).inputTokensDetails(ResponseUsage.InputTokensDetails.builder().cachedTokens(1).build())
            .outputTokens(4).outputTokensDetails(ResponseUsage.OutputTokensDetails.builder().reasoningTokens(2).build()).totalTokens(7).build())
        .user("").addOutput(ResponseOutputMessage.builder().id("msg_1").status(ResponseOutputMessage.Status.COMPLETED)
            .addContent(ResponseOutputText.builder().text(text).annotations(List.of()).build()).build()).build();
  }

  private static ResponseReasoningItem reasoning(String encryptedContent) {
    return ResponseReasoningItem.builder().id("reasoning_1").summary(List.of()).type(JsonValue.from("reasoning"))
        .encryptedContent(encryptedContent).build();
  }

  private static ResponseFunctionToolCall functionCall(String callId) {
    return ResponseFunctionToolCall.builder().callId(callId).name("calculator").arguments("{\"expression\":\"40 + 2\"}").build();
  }

  private static UnexpectedStatusCodeException httpError(int statusCode) {
    return UnexpectedStatusCodeException.builder()
        .statusCode(statusCode)
        .headers(Headers.builder().build())
        .error(ErrorObject.builder()
            .code("fixture_error")
            .message("upstream diagnostic")
            .param(Optional.empty())
            .type("fixture_error")
            .build())
        .build();
  }

  private static final class FakeClient implements OpenAiCompatibleResponsesClient {
    private final List<Response> responses;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<com.openai.models.responses.ResponseCreateParams> allParams = new ArrayList<>();
    private com.openai.models.responses.ResponseCreateParams params;
    private FakeStreamResponse stream;
    private RuntimeException failure;

    private FakeClient(Response... responses) { this.responses = List.of(responses); }

    @Override public Response create(com.openai.models.responses.ResponseCreateParams params) {
      this.params = params;
      allParams.add(params);
      int call = calls.getAndIncrement();
      if (failure != null) {
        throw failure;
      }
      return responses.get(call);
    }
    @Override public StreamResponse<ResponseStreamEvent> createStreaming(com.openai.models.responses.ResponseCreateParams params) {
      this.params = params; allParams.add(params); return stream;
    }
  }

  private static final class FakeStreamResponse implements StreamResponse<ResponseStreamEvent> {
    private final List<ResponseStreamEvent> events; private boolean closed;
    private FakeStreamResponse(List<ResponseStreamEvent> events) { this.events = events; }
    @Override public Stream<ResponseStreamEvent> stream() { return events.stream(); }
    @Override public void close() { closed = true; }
  }

  private static final class CollectingSubscriber implements Flow.Subscriber<LlmStreamEvent> {
    private final List<LlmStreamEvent> events = new ArrayList<>(); private final CountDownLatch finished = new CountDownLatch(1);
    @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
    @Override public void onNext(LlmStreamEvent event) { events.add(event); }
    @Override public void onError(Throwable error) { finished.countDown(); }
    @Override public void onComplete() { finished.countDown(); }
  }

  private static final class CancellingSubscriber implements Flow.Subscriber<LlmStreamEvent> {
    private final List<LlmStreamEvent> events = new ArrayList<>();
    private Flow.Subscription subscription;
    @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; subscription.request(Long.MAX_VALUE); }
    @Override public void onNext(LlmStreamEvent event) { events.add(event); subscription.cancel(); }
    @Override public void onError(Throwable error) { }
    @Override public void onComplete() { }
  }
}
