package org.congcong.algomentor.llm.openai;

import com.openai.core.http.StreamResponse;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseStreamEvent;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.metadata.LlmMetadataKeys;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在订阅线程中同步消费 OpenAI SDK 的阻塞流。
 *
 * <p>Agent loop 已经运行在独立工作线程中，并且下一步必须等待当前模型流结束。这里不再额外创建
 * OpenAI worker，而是让 Agent 工作线程顺序完成网络读取、事件投递和后续工具编排。</p>
 */
final class OpenAiStreamPublisher implements Flow.Publisher<LlmStreamEvent> {

  private static final Logger log = LoggerFactory.getLogger(OpenAiStreamPublisher.class);

  private final StreamResponse<ResponseStreamEvent> stream;
  private final OpenAiResponsesMapper mapper;
  private final LlmProviderId providerId;
  private final LlmModelId modelId;
  private final Map<String, StringBuilder> toolArgumentDeltas = new HashMap<>();
  private final AtomicBoolean subscribed = new AtomicBoolean(false);

  OpenAiStreamPublisher(
      StreamResponse<ResponseStreamEvent> stream,
      OpenAiResponsesMapper mapper,
      LlmProviderId providerId,
      LlmModelId modelId
  ) {
    this.stream = Objects.requireNonNull(stream, "stream must not be null");
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
    this.modelId = Objects.requireNonNull(modelId, "modelId must not be null");
  }

  @Override
  public void subscribe(Flow.Subscriber<? super LlmStreamEvent> subscriber) {
    Objects.requireNonNull(subscriber, "subscriber must not be null");
    if (!subscribed.compareAndSet(false, true)) {
      subscriber.onSubscribe(EmptySubscription.INSTANCE);
      subscriber.onError(new IllegalStateException("OpenAI stream supports only one subscriber"));
      return;
    }
    subscriber.onSubscribe(new OpenAiStreamSubscription(subscriber));
  }

  private void publishEvent(ResponseStreamEvent event, Consumer<LlmStreamEvent> sink) {
    if (event.isCreated()) {
      sink.accept(new LlmStreamEvent.MessageStart(providerId, modelId));
      return;
    }
    if (event.isOutputTextDelta()) {
      sink.accept(new LlmStreamEvent.ContentDelta(event.asOutputTextDelta().delta()));
      return;
    }
    if (event.isFunctionCallArgumentsDelta()) {
      var delta = event.asFunctionCallArgumentsDelta();
      toolArgumentDeltas.computeIfAbsent(delta.itemId(), ignored -> new StringBuilder()).append(delta.delta());
      sink.accept(new LlmStreamEvent.ToolCallDelta(delta.itemId(), delta.delta()));
      return;
    }
    if (event.isOutputItemAdded()) {
      event.asOutputItemAdded().item().functionCall()
          .ifPresent(call -> sink.accept(new LlmStreamEvent.ToolCallStart(call.callId(), call.name())));
      return;
    }
    if (event.isOutputItemDone()) {
      event.asOutputItemDone().item().functionCall()
          .map(this::withAccumulatedArguments)
          .map(mapper::toToolCall)
          .ifPresent(call -> sink.accept(new LlmStreamEvent.ToolCallEnd(call)));
      return;
    }
    if (event.isCompleted()) {
      var response = event.asCompleted().response();
      response.usage().map(mapper::toUsage).ifPresent(usage -> sink.accept(new LlmStreamEvent.Usage(usage)));
      sink.accept(new LlmStreamEvent.MessageEnd(
          mapper.finishReason(response),
          Map.of(LlmMetadataKeys.RESPONSE_ID, response.id())));
      return;
    }
    if (event.isIncomplete()) {
      var response = event.asIncomplete().response();
      sink.accept(new LlmStreamEvent.MessageEnd(
          LlmFinishReason.LENGTH,
          Map.of(LlmMetadataKeys.RESPONSE_ID, response.id())));
      return;
    }
    if (event.isFailed()) {
      var response = event.asFailed().response();
      sink.accept(new LlmStreamEvent.MessageEnd(
          LlmFinishReason.ERROR,
          Map.of(LlmMetadataKeys.RESPONSE_ID, response.id())));
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
      sink.accept(new LlmStreamEvent.Error(mapped));
    }
  }

  private LlmStreamEvent mapStreamFailure(Throwable error) {
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
    return new LlmStreamEvent.Error(mapped);
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

  private final class OpenAiStreamSubscription implements Flow.Subscription {
    private final Flow.Subscriber<? super LlmStreamEvent> subscriber;
    private final Queue<LlmStreamEvent> pendingEvents = new ArrayDeque<>();
    private final AtomicLong requested = new AtomicLong();
    private final AtomicInteger drainWork = new AtomicInteger();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean resourcesClosed = new AtomicBoolean(false);
    private final AtomicBoolean terminalSignalled = new AtomicBoolean(false);
    private Stream<ResponseStreamEvent> responseEvents;
    private Iterator<ResponseStreamEvent> iterator;
    private boolean sourceCompleted;
    private volatile Thread consumerThread;

    private OpenAiStreamSubscription(Flow.Subscriber<? super LlmStreamEvent> subscriber) {
      this.subscriber = subscriber;
    }

    @Override
    public void request(long count) {
      if (count <= 0) {
        signalInvalidDemand(count);
        return;
      }
      addDemand(count);
      drain();
    }

    @Override
    public void cancel() {
      if (!cancelled.compareAndSet(false, true)) {
        return;
      }
      closeResources();
      Thread thread = consumerThread;
      if (thread != null && thread != Thread.currentThread()) {
        thread.interrupt();
      }
    }

    private void drain() {
      if (drainWork.getAndIncrement() != 0) {
        return;
      }
      int missed = 1;
      consumerThread = Thread.currentThread();
      try {
        while (true) {
          long demand = requested.get();
          long emitted = 0;
          while (emitted < demand && !cancelled.get()) {
            LlmStreamEvent next = nextEvent();
            if (next == null) {
              break;
            }
            subscriber.onNext(next);
            emitted++;
          }
          if (emitted != 0 && demand != Long.MAX_VALUE) {
            requested.addAndGet(-emitted);
          }
          if (!cancelled.get() && sourceCompleted && pendingEvents.isEmpty()) {
            signalComplete();
            return;
          }
          missed = drainWork.addAndGet(-missed);
          if (missed == 0) {
            return;
          }
        }
      } catch (Throwable error) {
        cancel();
        if (terminalSignalled.compareAndSet(false, true)) {
          subscriber.onError(error);
        }
      } finally {
        consumerThread = null;
      }
    }

    private LlmStreamEvent nextEvent() {
      while (pendingEvents.isEmpty() && !sourceCompleted && !cancelled.get()) {
        try {
          ensureIterator();
          if (iterator.hasNext()) {
            publishEvent(iterator.next(), pendingEvents::add);
          } else {
            sourceCompleted = true;
            closeResources();
          }
        } catch (Throwable error) {
          if (cancelled.get()) {
            sourceCompleted = true;
            return null;
          }
          pendingEvents.add(mapStreamFailure(error));
          sourceCompleted = true;
          closeResources();
        }
      }
      return pendingEvents.poll();
    }

    private void ensureIterator() {
      if (iterator != null) {
        return;
      }
      responseEvents = stream.stream();
      iterator = responseEvents.iterator();
    }

    private void signalComplete() {
      closeResources();
      if (terminalSignalled.compareAndSet(false, true)) {
        subscriber.onComplete();
      }
    }

    private void signalInvalidDemand(long count) {
      if (!cancelled.compareAndSet(false, true)) {
        return;
      }
      closeResources();
      if (terminalSignalled.compareAndSet(false, true)) {
        subscriber.onError(new IllegalArgumentException("Flow request count must be positive: " + count));
      }
    }

    private void closeResources() {
      if (!resourcesClosed.compareAndSet(false, true)) {
        return;
      }
      if (responseEvents != null) {
        try {
          responseEvents.close();
        } catch (RuntimeException error) {
          log.debug("Failed to close OpenAI response event stream", error);
        }
      }
      try {
        stream.close();
      } catch (RuntimeException error) {
        log.debug("Failed to close OpenAI SDK stream response", error);
      }
    }

    private void addDemand(long count) {
      while (true) {
        long current = requested.get();
        long updated = current + count;
        if (updated < 0) {
          updated = Long.MAX_VALUE;
        }
        if (requested.compareAndSet(current, updated)) {
          return;
        }
      }
    }
  }

  private enum EmptySubscription implements Flow.Subscription {
    INSTANCE;

    @Override
    public void request(long count) {
    }

    @Override
    public void cancel() {
    }
  }
}
