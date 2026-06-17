package org.congcong.algomentor.llm.openai;

import com.openai.core.http.StreamResponse;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseStreamEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.congcong.algomentor.llm.core.metadata.LlmMetadataKeys;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

final class OpenAiStreamPublisher extends SubmissionPublisher<LlmStreamEvent> {

  private final StreamResponse<ResponseStreamEvent> stream;
  private final OpenAiResponsesMapper mapper;
  private final LlmProviderId providerId;
  private final LlmModelId modelId;
  private final Map<String, StringBuilder> toolArgumentDeltas = new HashMap<>();

  OpenAiStreamPublisher(
      StreamResponse<ResponseStreamEvent> stream,
      OpenAiResponsesMapper mapper,
      LlmProviderId providerId,
      LlmModelId modelId
  ) {
    this.stream = stream;
    this.mapper = mapper;
    this.providerId = providerId;
    this.modelId = modelId;
  }

  @Override
  public void subscribe(Flow.Subscriber<? super LlmStreamEvent> subscriber) {
    super.subscribe(subscriber);
    start();
  }

  private void start() {
    Thread worker = new Thread(() -> {
      try (stream) {
        stream.stream().forEach(this::publishEvent);
        close();
      } catch (Throwable error) {
        submit(new LlmStreamEvent.Error(OpenAiLlmExceptionMapper.map(error, providerId, modelId)));
        closeExceptionally(error);
      }
    }, "openai-llm-stream");
    worker.setDaemon(true);
    worker.start();
  }

  private void publishEvent(ResponseStreamEvent event) {
    if (event.isCreated()) {
      submit(new LlmStreamEvent.MessageStart(providerId, modelId));
      return;
    }
    if (event.isOutputTextDelta()) {
      submit(new LlmStreamEvent.ContentDelta(event.asOutputTextDelta().delta()));
      return;
    }
    if (event.isFunctionCallArgumentsDelta()) {
      var delta = event.asFunctionCallArgumentsDelta();
      toolArgumentDeltas.computeIfAbsent(delta.itemId(), ignored -> new StringBuilder()).append(delta.delta());
      submit(new LlmStreamEvent.ToolCallDelta(delta.itemId(), delta.delta()));
      return;
    }
    if (event.isOutputItemAdded()) {
      event.asOutputItemAdded().item().functionCall()
          .ifPresent(call -> submit(new LlmStreamEvent.ToolCallStart(call.callId(), call.name())));
      return;
    }
    if (event.isOutputItemDone()) {
      event.asOutputItemDone().item().functionCall()
          .map(this::withAccumulatedArguments)
          .map(mapper::toToolCall)
          .ifPresent(call -> submit(new LlmStreamEvent.ToolCallEnd(call)));
      return;
    }
    if (event.isCompleted()) {
      var response = event.asCompleted().response();
      response.usage().map(mapper::toUsage).ifPresent(usage -> submit(new LlmStreamEvent.Usage(usage)));
      submit(new LlmStreamEvent.MessageEnd(mapper.finishReason(response), Map.of(LlmMetadataKeys.RESPONSE_ID, response.id())));
      return;
    }
    if (event.isIncomplete()) {
      var response = event.asIncomplete().response();
      submit(new LlmStreamEvent.MessageEnd(LlmFinishReason.LENGTH, Map.of(LlmMetadataKeys.RESPONSE_ID, response.id())));
      return;
    }
    if (event.isFailed()) {
      var response = event.asFailed().response();
      submit(new LlmStreamEvent.MessageEnd(LlmFinishReason.ERROR, Map.of(LlmMetadataKeys.RESPONSE_ID, response.id())));
      return;
    }
    if (event.isError()) {
      var error = event.asError();
      submit(new LlmStreamEvent.Error(OpenAiLlmExceptionMapper.streamError(
          error.message(),
          providerId,
          modelId,
          Map.of(
              LlmMetadataKeys.PROVIDER, providerId.value(),
              LlmMetadataKeys.SEQUENCE_NUMBER, error.sequenceNumber()))));
    }
  }

  private ResponseFunctionToolCall withAccumulatedArguments(ResponseFunctionToolCall call) {
    StringBuilder arguments = toolArgumentDeltas.get(call.callId());
    if (arguments == null || arguments.isEmpty()) {
      arguments = toolArgumentDeltas.get(call.id().orElse(""));
    }
    if (arguments == null || arguments.isEmpty()) {
      return call;
    }
    return call.toBuilder().arguments(arguments.toString()).build();
  }
}
