package org.congcong.algomentor.llm.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.openai.core.JsonField;
import com.openai.core.http.Headers;
import com.openai.errors.SseException;
import com.openai.models.ErrorObject;
import com.openai.core.http.StreamResponse;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseCreatedEvent;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.ResponseTextDeltaEvent;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.ToolChoiceFunction;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.congcong.algomentor.common.trace.RequestTraceContext;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.junit.jupiter.api.Test;

class OpenAiLlmProviderTest {

  @Test
  void exposesProviderMetadata() {
    OpenAiLlmProvider provider = new OpenAiLlmProvider(enabledProperties(), new FakeResponsesClient(response("ok")));

    assertThat(provider.id().value()).isEqualTo("openai");
    assertThat(provider.models()).hasSize(1);
    assertThat(provider.capabilities().models()).containsKey("gpt-5.2");
    assertThat(provider.models().get(0).supportedCapabilities())
        .contains(LlmCapability.CHAT_COMPLETION, LlmCapability.STREAMING, LlmCapability.TOOL_CALLING);
  }

  @Test
  void rejectsCallsWhenProviderIsDisabled() {
    OpenAiLlmProvider provider = new OpenAiLlmProvider(new OpenAiLlmProperties(), new FakeResponsesClient(response("unused")));

    assertThatThrownBy(() -> provider.complete(textRequest()))
        .isInstanceOf(LlmException.class)
        .extracting("code")
        .isEqualTo(LlmErrorCode.INVALID_REQUEST);
  }

  @Test
  void mapsTextCompletionRequestAndResponse() {
    FakeResponsesClient client = new FakeResponsesClient(response("hello"));
    OpenAiLlmProvider provider = new OpenAiLlmProvider(enabledProperties(), client);

    LlmCompletionResult result = provider.complete(LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(OpenAiLlmProvider.PROVIDER_ID, LlmModelId.of("gpt-5.2")))
        .messages(List.of(LlmMessage.system("You teach algorithms."), LlmMessage.user("Explain BFS.")))
        .options(new LlmGenerationOptions(0.2, 0.9, 256, List.of(), null, null))
        .build());

    assertThat(result.message().text()).isEqualTo("hello");
    assertThat(result.finishReason()).isEqualTo(LlmFinishReason.STOP);
    assertThat(result.usage().inputTokens()).isEqualTo(3);
    assertThat(client.lastParams.model()).hasValueSatisfying(model -> assertThat(model.asString()).isEqualTo("gpt-5.2"));
    assertThat(client.lastParams.temperature()).contains(0.2);
    assertThat(client.lastParams.topP()).contains(0.9);
    assertThat(client.lastParams.maxOutputTokens()).contains(256L);
    assertThat(client.lastParams.input()).isPresent();
  }

  @Test
  void mapsToolsAndSpecificToolChoice() {
    FakeResponsesClient client = new FakeResponsesClient(response("tool"));
    OpenAiLlmProvider provider = new OpenAiLlmProvider(enabledProperties(), client);

    provider.complete(LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(OpenAiLlmProvider.PROVIDER_ID, LlmModelId.of("gpt-5.2")))
        .messages(List.of(LlmMessage.user("Find a problem.")))
        .tools(List.of(new LlmToolSpec(
            "search_problem",
            "Search an algorithm problem",
            JsonNodeFactory.instance.objectNode().put("type", "object"),
            true)))
        .toolChoice(LlmToolChoice.specific("search_problem"))
        .build());

    FunctionTool function = client.lastParams.tools().orElseThrow().get(0).asFunction();
    assertThat(function.name()).isEqualTo("search_problem");
    assertThat(function.strict()).contains(true);
    assertThat(client.lastParams.toolChoice().orElseThrow().asFunction())
        .extracting(ToolChoiceFunction::name)
        .isEqualTo("search_problem");
  }

  @Test
  void mapsToolCallResponseToToolCallsFinishReason() {
    FakeResponsesClient client = new FakeResponsesClient(toolCallResponse());
    OpenAiLlmProvider provider = new OpenAiLlmProvider(enabledProperties(), client);

    LlmCompletionResult result = provider.complete(textRequest());

    assertThat(result.finishReason()).isEqualTo(LlmFinishReason.TOOL_CALLS);
    assertThat(result.toolCalls()).hasSize(1);
    assertThat(result.toolCalls().get(0).name()).isEqualTo("fake_lookup");
    assertThat(result.toolCalls().get(0).arguments().get("topic").asText()).isEqualTo("two pointers");
  }

  @Test
  void mapsAssistantToolCallsAndToolResultsToContinuationInputItems() {
    FakeResponsesClient client = new FakeResponsesClient(response("done"));
    OpenAiLlmProvider provider = new OpenAiLlmProvider(enabledProperties(), client);
    LlmToolCall toolCall = new LlmToolCall(
        "call_1",
        "calculator",
        JsonNodeFactory.instance.objectNode().put("expression", "66 * 66 + 66 * 1236"));

    provider.complete(LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(OpenAiLlmProvider.PROVIDER_ID, LlmModelId.of("gpt-5.2")))
        .messages(List.of(
            LlmMessage.user("calculate"),
            LlmMessage.assistantToolCalls(List.of(toolCall)),
            LlmMessage.toolResult("call_1", JsonNodeFactory.instance.objectNode().put("value", "85932"))))
        .build());

    List<com.openai.models.responses.ResponseInputItem> input = client.lastParams.input()
        .orElseThrow()
        .asResponse()
        .stream()
        .toList();
    assertThat(input).hasSize(3);
    assertThat(input.get(1).asFunctionCall().callId()).isEqualTo("call_1");
    assertThat(input.get(1).asFunctionCall().arguments()).isEqualTo("{\"expression\":\"66 * 66 + 66 * 1236\"}");
    assertThat(input.get(2).asFunctionCallOutput().callId()).isEqualTo("call_1");
    assertThat(input.get(2).asFunctionCallOutput().output().asString()).isEqualTo("{\"value\":\"85932\"}");
  }


  @Test
  void mapsJsonResponseFormats() {
    FakeResponsesClient client = new FakeResponsesClient(response("{\"answer\":42}"));
    OpenAiLlmProvider provider = new OpenAiLlmProvider(enabledProperties(), client);

    LlmCompletionResult result = provider.complete(LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(OpenAiLlmProvider.PROVIDER_ID, LlmModelId.of("gpt-5.2")))
        .messages(List.of(LlmMessage.user("Return JSON.")))
        .responseFormat(new LlmResponseFormat.JsonObject())
        .build());

    ResponseTextConfig jsonObjectConfig = client.lastParams.text().orElseThrow();
    assertThat(jsonObjectConfig.format().orElseThrow().asJsonObject()).isInstanceOf(ResponseFormatJsonObject.class);
    assertThat(result.structuredOutput().get("answer").asInt()).isEqualTo(42);

    provider.complete(LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(OpenAiLlmProvider.PROVIDER_ID, LlmModelId.of("gpt-5.2")))
        .messages(List.of(LlmMessage.user("Return schema JSON.")))
        .responseFormat(new LlmResponseFormat.JsonSchema(
            "answer",
            JsonNodeFactory.instance.objectNode().put("type", "object"),
            true))
        .build());

    ResponseFormatTextJsonSchemaConfig schema = client.lastParams.text().orElseThrow().format().orElseThrow().asJsonSchema();
    assertThat(schema.name()).isEqualTo("answer");
    assertThat(schema.strict()).contains(true);
  }

  @Test
  void streamsOpenAiEventsAsCoreEvents() throws Exception {
    FakeResponsesClient client = new FakeResponsesClient(response("done"), List.of(
        ResponseStreamEvent.ofCreated(ResponseCreatedEvent.builder()
            .response(response(""))
            .sequenceNumber(1)
            .build()),
        ResponseStreamEvent.ofOutputTextDelta(ResponseTextDeltaEvent.builder()
            .contentIndex(0)
            .delta("hel")
            .itemId("msg_123")
            .logprobs(List.of())
            .outputIndex(0)
            .sequenceNumber(2)
            .build()),
        ResponseStreamEvent.ofCompleted(ResponseCompletedEvent.builder()
            .response(response("hello"))
            .sequenceNumber(3)
            .build())));
    OpenAiLlmProvider provider = new OpenAiLlmProvider(enabledProperties(), client);
    TestSubscriber subscriber = new TestSubscriber();

    provider.stream(textRequest()).subscribe(subscriber);

    assertThat(subscriber.finished.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(subscriber.events)
        .anySatisfy(event -> assertThat(event).isInstanceOf(LlmStreamEvent.MessageStart.class))
        .anySatisfy(event -> assertThat(event).isEqualTo(new LlmStreamEvent.ContentDelta("hel")))
        .anySatisfy(event -> assertThat(event).isInstanceOf(LlmStreamEvent.Usage.class))
        .anySatisfy(event -> assertThat(event).isInstanceOf(LlmStreamEvent.MessageEnd.class));
    assertThat(client.lastParams.input()).isPresent();
  }

  @Test
  void streamWorkerPropagatesRequestTraceContext() throws Exception {
    AtomicReference<String> observedRequestId = new AtomicReference<>();
    FakeResponsesClient client = new FakeResponsesClient(
        response("done"),
        new ObservingStreamResponse(
            observedRequestId,
            List.of(ResponseStreamEvent.ofCompleted(ResponseCompletedEvent.builder()
                .response(response("hello"))
                .sequenceNumber(1)
                .build()))));
    OpenAiLlmProvider provider = new OpenAiLlmProvider(enabledProperties(), client);
    TestSubscriber subscriber = new TestSubscriber();

    try (RequestTraceContext.RequestTraceScope ignored = RequestTraceContext.withRequestId("request-openai-1")) {
      provider.stream(textRequest()).subscribe(subscriber);
    }

    assertThat(subscriber.finished.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(observedRequestId).hasValue("request-openai-1");
  }

  @Test
  void cancellingOpenAiStreamClosesSdkStreamResponse() throws Exception {
    FakeResponsesClient client = new FakeResponsesClient(response("done"), List.of(
        ResponseStreamEvent.ofCreated(ResponseCreatedEvent.builder()
            .response(response(""))
            .sequenceNumber(1)
            .build()),
        ResponseStreamEvent.ofOutputTextDelta(ResponseTextDeltaEvent.builder()
            .contentIndex(0)
            .delta("hel")
            .itemId("msg_123")
            .logprobs(List.of())
            .outputIndex(0)
            .sequenceNumber(2)
            .build())));
    OpenAiLlmProvider provider = new OpenAiLlmProvider(enabledProperties(), client);
    CancellingTestSubscriber subscriber = new CancellingTestSubscriber();

    provider.stream(textRequest()).subscribe(subscriber);

    assertThat(subscriber.cancelled.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(((ListStreamResponse) client.lastStreamResponse).closed).isTrue();
  }

  @Test
  void mapsSseOverloadFailureToRetryableStreamError() throws Exception {
    FakeResponsesClient client = new FakeResponsesClient(
        response("unused"),
        new ThrowingStreamResponse(SseException.builder()
            .statusCode(200)
            .headers(Headers.builder().build())
            .error(ErrorObject.builder()
                .message("Our servers are currently overloaded. Please try again later.")
                .type("server_error")
                .code("overloaded")
                .param(Optional.empty())
                .build())
            .build()));
    OpenAiLlmProvider provider = new OpenAiLlmProvider(enabledProperties(), client);
    TestSubscriber subscriber = new TestSubscriber();

    provider.stream(textRequest()).subscribe(subscriber);

    assertThat(subscriber.finished.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(subscriber.error).isNull();
    assertThat(subscriber.events)
        .filteredOn(LlmStreamEvent.Error.class::isInstance)
        .singleElement()
        .satisfies(event -> {
          LlmException error = ((LlmStreamEvent.Error) event).error();
          assertThat(error.code()).isEqualTo(LlmErrorCode.PROVIDER_UNAVAILABLE);
          assertThat(error.retryable()).isTrue();
          assertThat(error.getMessage()).contains("currently overloaded");
        });
  }

  private static LlmCompletionRequest textRequest() {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(OpenAiLlmProvider.PROVIDER_ID, LlmModelId.of("gpt-5.2")))
        .messages(List.of(LlmMessage.user("hello")))
        .build();
  }

  private static OpenAiLlmProperties enabledProperties() {
    OpenAiLlmProperties properties = new OpenAiLlmProperties();
    properties.setEnabled(true);
    properties.setApiKey("test-key");
    return properties;
  }

  private static Response response(String text) {
    return Response.builder()
        .id("resp_123")
        .createdAt(1.0)
        .error(Optional.empty())
        .incompleteDetails(Optional.empty())
        .instructions(Optional.empty())
        .metadata(Optional.empty())
        .model("gpt-5.2")
        .parallelToolCalls(false)
        .toolChoice(com.openai.models.responses.ToolChoiceOptions.AUTO)
        .tools(List.of())
        .temperature(Optional.empty())
        .topP(Optional.empty())
        .background(Optional.empty())
        .completedAt(Optional.empty())
        .conversation(Optional.empty())
        .maxOutputTokens(Optional.empty())
        .maxToolCalls(Optional.empty())
        .moderation(Optional.empty())
        .previousResponseId(Optional.empty())
        .prompt(Optional.empty())
        .promptCacheRetention(Optional.empty())
        .reasoning(Optional.empty())
        .serviceTier(Optional.empty())
        .status(ResponseStatus.COMPLETED)
        .text(JsonField.ofNullable(null))
        .topLogprobs(Optional.empty())
        .truncation(Optional.empty())
        .usage(ResponseUsage.builder()
            .inputTokens(3)
            .inputTokensDetails(ResponseUsage.InputTokensDetails.builder().cachedTokens(1).build())
            .outputTokens(4)
            .outputTokensDetails(ResponseUsage.OutputTokensDetails.builder().reasoningTokens(2).build())
            .totalTokens(7)
            .build())
        .user("")
        .addOutput(ResponseOutputMessage.builder()
            .id("msg_123")
            .status(ResponseOutputMessage.Status.COMPLETED)
            .addContent(ResponseOutputText.builder()
                .text(text)
                .annotations(List.of())
                .build())
            .build())
        .build();
  }

  private static Response toolCallResponse() {
    return response("")
        .toBuilder()
        .output(List.of(ResponseOutputItem.ofFunctionCall(ResponseFunctionToolCall.builder()
            .callId("call_1")
            .name("fake_lookup")
            .arguments("{\"topic\":\"two pointers\"}")
            .build())))
        .build();
  }

  private static final class FakeResponsesClient implements OpenAiResponsesClient {
    private final Response response;
    private final List<ResponseStreamEvent> streamEvents;
    private final StreamResponse<ResponseStreamEvent> streamResponse;
    private ResponseCreateParams lastParams;
    private StreamResponse<ResponseStreamEvent> lastStreamResponse;

    private FakeResponsesClient(Response response) {
      this(response, List.of());
    }

    private FakeResponsesClient(Response response, List<ResponseStreamEvent> streamEvents) {
      this.response = response;
      this.streamEvents = streamEvents;
      this.streamResponse = null;
    }

    private FakeResponsesClient(Response response, StreamResponse<ResponseStreamEvent> streamResponse) {
      this.response = response;
      this.streamEvents = List.of();
      this.streamResponse = streamResponse;
    }

    @Override
    public Response create(ResponseCreateParams params) {
      this.lastParams = params;
      return response;
    }

    @Override
    public StreamResponse<ResponseStreamEvent> createStreaming(ResponseCreateParams params) {
      this.lastParams = params;
      this.lastStreamResponse = streamResponse == null ? new ListStreamResponse(streamEvents) : streamResponse;
      return lastStreamResponse;
    }
  }

  private static final class ListStreamResponse implements StreamResponse<ResponseStreamEvent> {
    private final List<ResponseStreamEvent> events;
    private volatile boolean closed;

    private ListStreamResponse(List<ResponseStreamEvent> events) {
      this.events = events;
    }

    @Override
    public java.util.stream.Stream<ResponseStreamEvent> stream() {
      return events.stream();
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  private static final class ThrowingStreamResponse implements StreamResponse<ResponseStreamEvent> {
    private final RuntimeException error;

    private ThrowingStreamResponse(RuntimeException error) {
      this.error = error;
    }

    @Override
    public java.util.stream.Stream<ResponseStreamEvent> stream() {
      return java.util.stream.Stream.generate(() -> {
        throw error;
      });
    }

    @Override
    public void close() {
    }
  }

  private static final class ObservingStreamResponse implements StreamResponse<ResponseStreamEvent> {
    private final AtomicReference<String> observedRequestId;
    private final List<ResponseStreamEvent> events;

    private ObservingStreamResponse(
        AtomicReference<String> observedRequestId,
        List<ResponseStreamEvent> events
    ) {
      this.observedRequestId = observedRequestId;
      this.events = events;
    }

    @Override
    public Stream<ResponseStreamEvent> stream() {
      observedRequestId.set(RequestTraceContext.currentRequestId().orElse(null));
      return events.stream();
    }

    @Override
    public void close() {
    }
  }

  private static final class TestSubscriber implements java.util.concurrent.Flow.Subscriber<LlmStreamEvent> {
    private final List<LlmStreamEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile Throwable error;

    @Override
    public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(LlmStreamEvent item) {
      events.add(item);
      if (item instanceof LlmStreamEvent.MessageEnd || item instanceof LlmStreamEvent.Error) {
        finished.countDown();
      }
    }

    @Override
    public void onError(Throwable throwable) {
      error = throwable;
      finished.countDown();
    }

    @Override
    public void onComplete() {
      finished.countDown();
    }
  }

  private static final class CancellingTestSubscriber implements java.util.concurrent.Flow.Subscriber<LlmStreamEvent> {
    private final CountDownLatch cancelled = new CountDownLatch(1);
    private java.util.concurrent.Flow.Subscription subscription;

    @Override
    public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(LlmStreamEvent item) {
      subscription.cancel();
      cancelled.countDown();
    }

    @Override
    public void onError(Throwable throwable) {
      cancelled.countDown();
    }

    @Override
    public void onComplete() {
      cancelled.countDown();
    }
  }
}
