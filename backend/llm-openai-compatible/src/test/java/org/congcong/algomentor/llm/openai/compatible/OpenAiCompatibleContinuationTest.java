package org.congcong.algomentor.llm.openai.compatible;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.openai.core.JsonField;
import com.openai.core.JsonValue;
import com.openai.core.ObjectMappers;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseIncompleteEvent;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItemDoneEvent;
import com.openai.models.responses.ResponseReasoningItem;
import com.openai.models.responses.ResponseReasoningTextDeltaEvent;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.ToolChoiceOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderContinuation;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleContinuationTest {

  private static final LlmModelId MODEL_ID = LlmModelId.of("compatible-test-model");
  private static final OpenAiCompatibleProviderProfile CONTINUATION_PROFILE = new TestProfile(
      LlmProviderType.of("compatible-continuation-test"), true);
  private static final OpenAiCompatibleProviderProfile OTHER_PROFILE = new TestProfile(
      LlmProviderType.of("compatible-other-test"), true);

  @Test
  void restoresReasoningBeforeFunctionCallAndToolResultForTheNextStep() {
    OpenAiCompatibleResponsesMapper mapper = mapper(CONTINUATION_PROFILE);
    LlmCompletionResult firstResult = mapper.toResult(toolResponse("call_1", "reasoning-sentinel-1"));

    assertThat(firstResult.providerContinuation()).isNotNull();
    var secondParams = mapper.toParams(request(List.of(
        LlmMessage.user("calculate"),
        LlmMessage.assistantToolCalls(firstResult.toolCalls(), firstResult.providerContinuation()),
        LlmMessage.toolResult("call_1", JsonNodeFactory.instance.objectNode().put("value", 42)))), MODEL_ID);

    List<ResponseInputItem> input = secondParams.input().orElseThrow().asResponse().stream().toList();
    assertThat(input).hasSize(4);
    assertThat(input.get(1).isReasoning()).isTrue();
    assertThat(input.get(1).asReasoning().encryptedContent()).contains("reasoning-sentinel-1");
    assertThat(input.get(2).asFunctionCall().callId()).isEqualTo("call_1");
    assertThat(input.get(3).asFunctionCallOutput().callId()).isEqualTo("call_1");
  }

  @Test
  void keepsEachToolStepContinuationIndependent() {
    CapturingResponsesClient client = new CapturingResponsesClient(
        List.of(toolResponse("call_1", "reasoning-sentinel-1"), toolResponse("call_2", "reasoning-sentinel-2")));
    OpenAiCompatibleProviderClient providerClient = new OpenAiCompatibleProviderClient(client, CONTINUATION_PROFILE);

    LlmCompletionResult firstResult = providerClient.complete(MODEL_ID, request(List.of(LlmMessage.user("first"))));
    LlmCompletionResult secondResult = providerClient.complete(MODEL_ID, request(List.of(
        LlmMessage.user("first"),
        LlmMessage.assistantToolCalls(firstResult.toolCalls(), firstResult.providerContinuation()),
        LlmMessage.toolResult("call_1", JsonNodeFactory.instance.objectNode().put("value", "one")))));

    assertThat(client.params).hasSize(2);
    List<ResponseInputItem> secondInput = client.params.get(1).input().orElseThrow().asResponse().stream().toList();
    assertThat(secondInput.get(1).asReasoning().encryptedContent()).contains("reasoning-sentinel-1");
    assertThat(secondResult.toolCalls()).extracting(LlmToolCall::id).containsExactly("call_2");
    assertThat(secondResult.providerContinuation().payload().toString()).contains("reasoning-sentinel-2");
  }

  @Test
  void rejectsMismatchedOrMalformedContinuationBeforeCallingSdk() {
    CapturingResponsesClient client = new CapturingResponsesClient(List.of(toolResponse("call_1", "unused")));
    OpenAiCompatibleProviderClient providerClient = new OpenAiCompatibleProviderClient(client, CONTINUATION_PROFILE);
    LlmToolCall toolCall = toolCall("call_1");

    assertThatThrownBy(() -> providerClient.complete(MODEL_ID, request(List.of(
        LlmMessage.assistantToolCalls(List.of(toolCall), new LlmProviderContinuation(
            OTHER_PROFILE.providerType(), JsonNodeFactory.instance.arrayNode()))))))
        .isInstanceOf(LlmException.class)
        .extracting("code")
        .isEqualTo(LlmErrorCode.INVALID_REQUEST);

    assertThatThrownBy(() -> providerClient.complete(MODEL_ID, request(List.of(
        LlmMessage.assistantToolCalls(List.of(toolCall), new LlmProviderContinuation(
            CONTINUATION_PROFILE.providerType(), JsonNodeFactory.instance.objectNode().put("type", "reasoning")))))))
        .isInstanceOf(LlmException.class)
        .extracting("code")
        .isEqualTo(LlmErrorCode.INVALID_REQUEST);

    assertThat(client.calls.get()).isZero();
  }

  @Test
  void collectsCompletedReasoningItemsWithoutPublishingReasoningText() throws Exception {
    ResponseReasoningItem reasoning = reasoning("reasoning-sentinel-stream");
    ResponseFunctionToolCall toolCall = functionCall("call_1");
    var stream = new ListStreamResponse(List.of(
        ResponseStreamEvent.ofReasoningTextDelta(ResponseReasoningTextDeltaEvent.builder()
            .contentIndex(0)
            .delta("never-visible")
            .itemId("reasoning_1")
            .outputIndex(0)
            .sequenceNumber(1)
            .build()),
        ResponseStreamEvent.ofOutputItemDone(ResponseOutputItemDoneEvent.builder()
            .item(reasoning)
            .outputIndex(0)
            .sequenceNumber(2)
            .build()),
        ResponseStreamEvent.ofOutputItemDone(ResponseOutputItemDoneEvent.builder()
            .item(toolCall)
            .outputIndex(1)
            .sequenceNumber(3)
            .build()),
        ResponseStreamEvent.ofCompleted(ResponseCompletedEvent.builder()
            .response(toolResponse("call_1", "response-reasoning-not-used"))
            .sequenceNumber(4)
            .build())));

    RecordingSubscriber subscriber = new RecordingSubscriber();
    new OpenAiCompatibleStreamPublisher(stream, mapper(CONTINUATION_PROFILE), CONTINUATION_PROFILE, MODEL_ID)
        .subscribe(subscriber);

    assertThat(subscriber.completed.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(subscriber.events).noneMatch(LlmStreamEvent.ContentDelta.class::isInstance);
    List<LlmStreamEvent.MessageEnd> messageEnds = subscriber.events.stream()
        .filter(LlmStreamEvent.MessageEnd.class::isInstance)
        .map(LlmStreamEvent.MessageEnd.class::cast)
        .toList();
    assertThat(messageEnds).singleElement();
    LlmStreamEvent.MessageEnd messageEnd = messageEnds.get(0);
    assertThat(messageEnd.finishReason()).isEqualTo(org.congcong.algomentor.llm.core.response.LlmFinishReason.TOOL_CALLS);
    assertThat(messageEnd.providerContinuation()).isNotNull();
    assertThat(messageEnd.providerContinuation().payload().toString()).contains("reasoning-sentinel-stream");
  }

  @Test
  void dropsCollectedContinuationForIncompleteOrCancelledStreams() throws Exception {
    var incompleteStream = new ListStreamResponse(List.of(
        outputItemDone(reasoning("reasoning-sentinel-incomplete"), 1),
        outputItemDone(functionCall("call_1"), 2),
        ResponseStreamEvent.ofIncomplete(ResponseIncompleteEvent.builder()
            .response(responseWithReasoning(ResponseStatus.INCOMPLETE, true))
            .sequenceNumber(3)
            .build())));
    RecordingSubscriber incompleteSubscriber = new RecordingSubscriber();
    new OpenAiCompatibleStreamPublisher(
        incompleteStream, mapper(CONTINUATION_PROFILE), CONTINUATION_PROFILE, MODEL_ID).subscribe(incompleteSubscriber);

    assertThat(incompleteSubscriber.completed.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(incompleteSubscriber.events)
        .filteredOn(LlmStreamEvent.MessageEnd.class::isInstance)
        .singleElement()
        .satisfies(event -> assertThat(((LlmStreamEvent.MessageEnd) event).providerContinuation()).isNull());

    ListStreamResponse cancellableStream = new ListStreamResponse(List.of(
        outputItemDone(reasoning("reasoning-sentinel-cancelled"), 1),
        outputItemDone(functionCall("call_1"), 2),
        ResponseStreamEvent.ofCompleted(ResponseCompletedEvent.builder()
            .response(toolResponse("call_1", "response-reasoning-not-used"))
            .sequenceNumber(3)
            .build())));
    CancellingSubscriber cancellingSubscriber = new CancellingSubscriber();
    new OpenAiCompatibleStreamPublisher(
        cancellableStream, mapper(CONTINUATION_PROFILE), CONTINUATION_PROFILE, MODEL_ID).subscribe(cancellingSubscriber);

    assertThat(cancellableStream.closed).isTrue();
    assertThat(cancellingSubscriber.events).noneMatch(LlmStreamEvent.MessageEnd.class::isInstance);
  }

  @Test
  void createsContinuationOnlyForCompletedToolResponsesAndUsesRequestAllowlist() {
    OpenAiCompatibleResponsesMapper mapper = mapper(CONTINUATION_PROFILE);

    assertThat(mapper.toResult(responseWithReasoning(ResponseStatus.COMPLETED, false)).providerContinuation()).isNull();
    assertThat(mapper.toResult(responseWithReasoning(ResponseStatus.INCOMPLETE, true)).providerContinuation()).isNull();

    JsonNode body = ObjectMappers.jsonMapper().valueToTree(mapper.toParams(LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(CONTINUATION_PROFILE.providerId(), MODEL_ID))
        .messages(List.of(LlmMessage.user("hello")))
        .options(new LlmGenerationOptions(0.2, 0.8, 64, List.of("stop"), 7L, null, LlmReasoningEffort.HIGH))
        .build(), MODEL_ID)._body());

    assertThat(body.fieldNames()).toIterable()
        .containsOnly("model", "input", "temperature", "top_p", "max_output_tokens", "reasoning");
    assertThat(body.toString()).doesNotContain(
        "stop", "seed", "store", "previous_response_id", "conversation", "metadata", "include", "service_tier");
  }

  private static OpenAiCompatibleResponsesMapper mapper(OpenAiCompatibleProviderProfile profile) {
    return new OpenAiCompatibleResponsesMapper(new ObjectMapper(), profile);
  }

  private static LlmCompletionRequest request(List<LlmMessage> messages) {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(CONTINUATION_PROFILE.providerId(), MODEL_ID))
        .messages(messages)
        .build();
  }

  private static Response toolResponse(String callId, String encryptedContent) {
    return responseWithReasoning(ResponseStatus.COMPLETED, true).toBuilder()
        .output(List.of(
            com.openai.models.responses.ResponseOutputItem.ofReasoning(reasoning(encryptedContent)),
            com.openai.models.responses.ResponseOutputItem.ofFunctionCall(functionCall(callId))))
        .build();
  }

  private static Response responseWithReasoning(ResponseStatus status, boolean withToolCall) {
    Response.Builder builder = baseResponse(status)
        .addOutput(reasoning("reasoning-sentinel-final"));
    if (withToolCall) {
      builder.addOutput(functionCall("call_1"));
    }
    return builder.build();
  }

  private static Response.Builder baseResponse(ResponseStatus status) {
    return Response.builder()
        .id("resp_123")
        .createdAt(1.0)
        .error(Optional.empty())
        .incompleteDetails(Optional.empty())
        .instructions(Optional.empty())
        .metadata(Optional.empty())
        .model(MODEL_ID.value())
        .parallelToolCalls(false)
        .toolChoice(ToolChoiceOptions.AUTO)
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
        .status(status)
        .text(JsonField.ofNullable(null))
        .topLogprobs(Optional.empty())
        .truncation(Optional.empty())
        .usage(ResponseUsage.builder()
            .inputTokens(1)
            .inputTokensDetails(ResponseUsage.InputTokensDetails.builder().cachedTokens(0).build())
            .outputTokens(1)
            .outputTokensDetails(ResponseUsage.OutputTokensDetails.builder().reasoningTokens(1).build())
            .totalTokens(2)
            .build())
        .user("");
  }

  private static ResponseReasoningItem reasoning(String encryptedContent) {
    return ResponseReasoningItem.builder()
        .id("reasoning_1")
        .summary(List.of())
        .type(JsonValue.from("reasoning"))
        .encryptedContent(encryptedContent)
        .build();
  }

  private static ResponseFunctionToolCall functionCall(String callId) {
    return ResponseFunctionToolCall.builder()
        .callId(callId)
        .name("calculator")
        .arguments("{\"expression\":\"40 + 2\"}")
        .build();
  }

  private static ResponseStreamEvent outputItemDone(Object item, long sequenceNumber) {
    ResponseOutputItemDoneEvent.Builder builder = ResponseOutputItemDoneEvent.builder()
        .outputIndex(sequenceNumber - 1)
        .sequenceNumber(sequenceNumber);
    if (item instanceof ResponseReasoningItem reasoning) {
      builder.item(reasoning);
    } else if (item instanceof ResponseFunctionToolCall functionCall) {
      builder.item(functionCall);
    } else {
      throw new IllegalArgumentException("Unsupported output item fixture");
    }
    return ResponseStreamEvent.ofOutputItemDone(builder.build());
  }

  private static LlmToolCall toolCall(String callId) {
    return new LlmToolCall(
        callId,
        "calculator",
        JsonNodeFactory.instance.objectNode().put("expression", "40 + 2"));
  }

  private static final class CapturingResponsesClient implements OpenAiCompatibleResponsesClient {
    private final List<Response> responses;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<com.openai.models.responses.ResponseCreateParams> params = new ArrayList<>();

    private CapturingResponsesClient(List<Response> responses) {
      this.responses = responses;
    }

    @Override
    public Response create(com.openai.models.responses.ResponseCreateParams params) {
      this.params.add(params);
      return responses.get(calls.getAndIncrement());
    }

    @Override
    public StreamResponse<ResponseStreamEvent> createStreaming(com.openai.models.responses.ResponseCreateParams params) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class ListStreamResponse implements StreamResponse<ResponseStreamEvent> {
    private final List<ResponseStreamEvent> events;
    private boolean closed;

    private ListStreamResponse(List<ResponseStreamEvent> events) {
      this.events = events;
    }

    @Override
    public Stream<ResponseStreamEvent> stream() {
      return events.stream();
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  private static final class RecordingSubscriber implements Flow.Subscriber<LlmStreamEvent> {
    private final List<LlmStreamEvent> events = new ArrayList<>();
    private final CountDownLatch completed = new CountDownLatch(1);

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(LlmStreamEvent event) {
      events.add(event);
    }

    @Override
    public void onError(Throwable error) {
      completed.countDown();
    }

    @Override
    public void onComplete() {
      completed.countDown();
    }
  }

  private static final class CancellingSubscriber implements Flow.Subscriber<LlmStreamEvent> {
    private final List<LlmStreamEvent> events = new ArrayList<>();
    private Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(LlmStreamEvent event) {
      events.add(event);
      if (event instanceof LlmStreamEvent.ToolCallEnd) {
        subscription.cancel();
      }
    }

    @Override
    public void onError(Throwable error) {
    }

    @Override
    public void onComplete() {
    }
  }

  private record TestProfile(LlmProviderType providerType, boolean requiresContinuation)
      implements OpenAiCompatibleProviderProfile {

    @Override
    public String displayName() {
      return "Compatible continuation test provider";
    }

    @Override
    public Set<LlmCapability> supportedCapabilities() {
      return Set.of(LlmCapability.CHAT_COMPLETION, LlmCapability.STREAMING, LlmCapability.TOOL_CALLING);
    }

    @Override
    public Set<LlmReasoningEffort> acceptedReasoningEfforts() {
      return Set.of();
    }

    @Override
    public void validateRequest(LlmModelId modelId, LlmCompletionRequest request) {
    }

    @Override
    public boolean requiresReasoningContinuationForToolCalls() {
      return requiresContinuation;
    }
  }
}
