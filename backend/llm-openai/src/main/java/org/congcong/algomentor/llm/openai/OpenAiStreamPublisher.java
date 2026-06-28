package org.congcong.algomentor.llm.openai;

import com.openai.core.http.StreamResponse;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseStreamEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.common.trace.RequestTraceContext;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.metadata.LlmMetadataKeys;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OpenAiStreamPublisher extends SubmissionPublisher<LlmStreamEvent> {

  private static final Logger log = LoggerFactory.getLogger(OpenAiStreamPublisher.class);

  private final StreamResponse<ResponseStreamEvent> stream;
  private final OpenAiResponsesMapper mapper;
  private final LlmProviderId providerId;
  private final LlmModelId modelId;
  private final Map<String, StringBuilder> toolArgumentDeltas = new HashMap<>();
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private volatile Thread worker;

  OpenAiStreamPublisher(
      StreamResponse<ResponseStreamEvent> stream,
      OpenAiResponsesMapper mapper,
      LlmProviderId providerId,
      LlmModelId modelId
  ) {
    super(RequestTraceContext.contextAwareExecutor(java.util.concurrent.ForkJoinPool.commonPool()), Flow.defaultBufferSize());
    this.stream = stream;
    this.mapper = mapper;
    this.providerId = providerId;
    this.modelId = modelId;
  }

  @Override
  public void subscribe(Flow.Subscriber<? super LlmStreamEvent> subscriber) {
    super.subscribe(new CloseOnCancelSubscriber(subscriber));
    start();
  }

  private void start() {
    if (!started.compareAndSet(false, true)) {
      return;
    }
    Thread worker = new Thread(RequestTraceContext.wrap(() -> {
      try (stream) {
        stream.stream()
            .takeWhile(ignored -> !cancelled.get())
            .forEach(this::publishEvent);
        close();
      } catch (Throwable error) {
        if (cancelled.get()) {
          close();
          return;
        }
        LlmException mapped = OpenAiLlmExceptionMapper.map(error, providerId, modelId);
        log.warn(
            "OpenAI stream failed while consuming events. provider={} model={} code={} retryable={} metadata={} causeType={} causeMessage={}",
            providerId.value(),
            modelId.value(),
            mapped.code(),
            mapped.retryable(),
            mapped.metadata(),
            causeType(mapped),
            causeMessage(mapped));
        submit(new LlmStreamEvent.Error(mapped));
        close();
      }
    }), "openai-llm-stream");
    this.worker = worker;
    worker.setDaemon(true);
    worker.start();
  }

  private void cancelStream(Flow.Subscription subscription) {
    cancelled.set(true);
    subscription.cancel();
    stream.close();
    Thread thread = worker;
    if (thread != null) {
      thread.interrupt();
    }
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
      LlmException mapped = OpenAiLlmExceptionMapper.streamError(
          error.message(),
          providerId,
          modelId,
          Map.of(
              LlmMetadataKeys.PROVIDER, providerId.value(),
              LlmMetadataKeys.SEQUENCE_NUMBER, error.sequenceNumber()));
      log.warn(
          "OpenAI stream returned error event. provider={} model={} sequenceNumber={} code={} retryable={} message={}",
          providerId.value(),
          modelId.value(),
          error.sequenceNumber(),
          mapped.code(),
          mapped.retryable(),
          mapped.getMessage());
      submit(new LlmStreamEvent.Error(mapped));
    }
  }

  private String causeType(Throwable error) {
    Throwable cause = error.getCause();
    return cause == null ? "none" : cause.getClass().getName();
  }

  private String causeMessage(Throwable error) {
    Throwable cause = error.getCause();
    if (cause == null || cause.getMessage() == null || cause.getMessage().isBlank()) {
      return "";
    }
    return cause.getMessage();
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

  private final class CloseOnCancelSubscriber implements Flow.Subscriber<LlmStreamEvent> {
    private final Flow.Subscriber<? super LlmStreamEvent> delegate;

    private CloseOnCancelSubscriber(Flow.Subscriber<? super LlmStreamEvent> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      delegate.onSubscribe(new Flow.Subscription() {
        @Override
        public void request(long n) {
          subscription.request(n);
        }

        @Override
        public void cancel() {
          cancelStream(subscription);
        }
      });
    }

    @Override
    public void onNext(LlmStreamEvent item) {
      if (!cancelled.get()) {
        delegate.onNext(item);
      }
    }

    @Override
    public void onError(Throwable throwable) {
      delegate.onError(throwable);
    }

    @Override
    public void onComplete() {
      delegate.onComplete();
    }
  }
}
