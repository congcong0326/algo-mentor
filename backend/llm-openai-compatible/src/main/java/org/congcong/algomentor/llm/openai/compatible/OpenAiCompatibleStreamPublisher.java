package org.congcong.algomentor.llm.openai.compatible;

import com.openai.core.http.StreamResponse;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseReasoningItem;
import com.openai.models.responses.ResponseStreamEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.metadata.LlmMetadataKeys;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在订阅线程中同步消费 SDK 的阻塞流。
 *
 * <p>Agent loop 已经运行在独立工作线程中，并且下一步必须等待当前模型流结束。这里不再额外创建
 * transport worker，而是让 Agent 工作线程顺序完成网络读取、事件投递和后续工具编排。</p>
 */
public final class OpenAiCompatibleStreamPublisher implements Flow.Publisher<LlmStreamEvent> {

  private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleStreamPublisher.class);
  /** overload 首次请求计入总次数，最多尝试 5 次。 */
  private static final int SERVER_OVERLOAD_MAX_ATTEMPTS = 5;
  private static final long STREAM_RETRY_INITIAL_BACKOFF_MILLIS = 200L;
  private static final long STREAM_RETRY_MAX_BACKOFF_MILLIS = 2_000L;

  private final Supplier<StreamResponse<ResponseStreamEvent>> streamFactory;
  private final OpenAiCompatibleResponsesMapper mapper;
  private final OpenAiCompatibleProviderProfile profile;
  private final LlmModelId modelId;
  private final int maxRetries;
  private final Map<String, StringBuilder> toolArgumentDeltas = new HashMap<>();
  private final List<ResponseReasoningItem> reasoningItems = new ArrayList<>();
  private final List<LlmToolCall> completedToolCalls = new ArrayList<>();
  private final AtomicBoolean subscribed = new AtomicBoolean(false);

  public OpenAiCompatibleStreamPublisher(
      StreamResponse<ResponseStreamEvent> stream,
      OpenAiCompatibleResponsesMapper mapper,
      OpenAiCompatibleProviderProfile profile,
      LlmModelId modelId
  ) {
    this(() -> stream, mapper, profile, modelId, 0);
  }

  public OpenAiCompatibleStreamPublisher(
      Supplier<StreamResponse<ResponseStreamEvent>> streamFactory,
      OpenAiCompatibleResponsesMapper mapper,
      OpenAiCompatibleProviderProfile profile,
      LlmModelId modelId,
      int maxRetries
  ) {
    this.streamFactory = Objects.requireNonNull(streamFactory, "stream factory must not be null");
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    this.profile = Objects.requireNonNull(profile, "profile must not be null");
    this.modelId = Objects.requireNonNull(modelId, "modelId must not be null");
    if (maxRetries < 0) {
      throw new IllegalArgumentException("maxRetries must not be negative");
    }
    this.maxRetries = maxRetries;
  }

  @Override
  public void subscribe(Flow.Subscriber<? super LlmStreamEvent> subscriber) {
    Objects.requireNonNull(subscriber, "subscriber must not be null");
    if (!subscribed.compareAndSet(false, true)) {
      subscriber.onSubscribe(EmptySubscription.INSTANCE);
      subscriber.onError(new IllegalStateException(profile.displayName() + " stream supports only one subscriber"));
      return;
    }
    subscriber.onSubscribe(new OpenAiStreamSubscription(subscriber));
  }

  private void publishEvent(ResponseStreamEvent event, Consumer<LlmStreamEvent> sink) {
    if (event.isCreated()) {
      sink.accept(new LlmStreamEvent.MessageStart(profile.providerId(), modelId));
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
      var item = event.asOutputItemDone().item();
      item.reasoning().ifPresent(reasoningItems::add);
      item.functionCall()
          .map(this::withAccumulatedArguments)
          .map(mapper::toToolCall)
          .ifPresent(call -> {
            completedToolCalls.add(call);
            sink.accept(new LlmStreamEvent.ToolCallEnd(call));
          });
      return;
    }
    if (event.isCompleted()) {
      var response = event.asCompleted().response();
      response.usage().map(mapper::toUsage).ifPresent(usage -> sink.accept(new LlmStreamEvent.Usage(usage)));
      var continuation = profile.requiresReasoningContinuationForToolCalls() && !completedToolCalls.isEmpty()
          ? new OpenAiCompatibleReasoningContinuationCodec(profile, modelId).create(reasoningItems)
          : null;
      sink.accept(new LlmStreamEvent.MessageEnd(
          mapper.finishReason(response, completedToolCalls),
          Map.of(LlmMetadataKeys.RESPONSE_ID, response.id()),
          continuation));
      clearCollectedState();
      return;
    }
    if (event.isIncomplete()) {
      var response = event.asIncomplete().response();
      clearCollectedState();
      sink.accept(new LlmStreamEvent.MessageEnd(
          LlmFinishReason.LENGTH,
          Map.of(LlmMetadataKeys.RESPONSE_ID, response.id())));
      return;
    }
    if (event.isFailed()) {
      var response = event.asFailed().response();
      clearCollectedState();
      sink.accept(new LlmStreamEvent.MessageEnd(
          LlmFinishReason.ERROR,
          Map.of(LlmMetadataKeys.RESPONSE_ID, response.id())));
      return;
    }
    if (event.isError()) {
      clearCollectedState();
      var error = event.asError();
      LlmException mapped = OpenAiCompatibleExceptionMapper.streamError(
          error.message(),
          profile,
          modelId,
          Map.of(
              LlmMetadataKeys.PROVIDER, profile.providerId().value(),
              LlmMetadataKeys.SEQUENCE_NUMBER, error.sequenceNumber()));
      log.warn(
          "{} stream returned error event. provider={} model={} sequenceNumber={} code={} retryable={} message={}",
          profile.displayName(),
          profile.providerId().value(),
          modelId.value(),
          error.sequenceNumber(),
          mapped.code(),
          mapped.retryable(),
          mapped.getMessage());
      sink.accept(new LlmStreamEvent.Error(mapped));
    }
  }

  private LlmStreamEvent mapStreamFailure(Throwable error) {
    clearCollectedState();
    LlmException mapped = OpenAiCompatibleExceptionMapper.map(error, profile, modelId);
    log.warn(
        "{} stream failed while consuming events. provider={} model={} code={} retryable={} causeType={}",
        profile.displayName(),
        profile.providerId().value(),
        modelId.value(),
        mapped.code(),
        mapped.retryable(),
        causeType(mapped));
    return new LlmStreamEvent.Error(mapped);
  }

  private String causeType(Throwable error) {
    Throwable cause = error.getCause();
    return cause == null ? "none" : cause.getClass().getName();
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

  private void clearCollectedState() {
    toolArgumentDeltas.clear();
    reasoningItems.clear();
    completedToolCalls.clear();
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
    private StreamResponse<ResponseStreamEvent> currentStream;
    private Iterator<ResponseStreamEvent> iterator;
    private boolean sourceCompleted;
    private boolean emittedAnyEvent;
    private int retryCount;
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
      clearCollectedState();
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
            publishEvent(iterator.next(), event -> {
              emittedAnyEvent = true;
              pendingEvents.add(event);
            });
          } else {
            sourceCompleted = true;
            closeResources();
          }
        } catch (Throwable error) {
          if (cancelled.get()) {
            sourceCompleted = true;
            return null;
          }
          LlmException mapped = OpenAiCompatibleExceptionMapper.map(error, profile, modelId);
          if (canRetry(mapped)) {
            retryCount++;
            int maxAttempts = maxAttempts(mapped);
            log.warn(
                "{} stream failed before first event; retrying. provider={} model={} retryAttempt={} maxAttempts={} code={} causeType={}",
                profile.displayName(),
                profile.providerId().value(),
                modelId.value(),
                retryCount,
                maxAttempts,
                mapped.code(),
                causeType(mapped));
            closeAttemptResources();
            clearCollectedState();
            iterator = null;
            backoffBeforeRetry();
            continue;
          }
          pendingEvents.add(mapStreamFailure(mapped));
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
      currentStream = Objects.requireNonNull(streamFactory.get(), "stream factory returned null");
      responseEvents = currentStream.stream();
      iterator = responseEvents.iterator();
    }

    private boolean canRetry(LlmException error) {
      return !emittedAnyEvent
          && error.retryable()
          && retryCount + 1 < maxAttempts(error)
          && !cancelled.get();
    }

    private int maxAttempts(LlmException error) {
      return OpenAiCompatibleExceptionMapper.isServerOverloaded(error)
          ? SERVER_OVERLOAD_MAX_ATTEMPTS
          : maxRetries + 1;
    }

    private void backoffBeforeRetry() {
      long delay = Math.min(
          STREAM_RETRY_MAX_BACKOFF_MILLIS,
          STREAM_RETRY_INITIAL_BACKOFF_MILLIS << Math.min(retryCount - 1, 3));
      try {
        Thread.sleep(delay);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        if (!cancelled.get()) {
          throw new IllegalStateException("Interrupted while retrying LLM stream", interrupted);
        }
      }
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
      closeAttemptResources();
    }

    private void closeAttemptResources() {
      if (responseEvents != null) {
        try {
          responseEvents.close();
        } catch (RuntimeException error) {
          log.debug("Failed to close {} response event stream", profile.displayName(), error);
        }
        responseEvents = null;
      }
      if (currentStream != null) {
        try {
          currentStream.close();
        } catch (RuntimeException error) {
          log.debug("Failed to close {} SDK stream response", profile.displayName(), error);
        }
        currentStream = null;
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
