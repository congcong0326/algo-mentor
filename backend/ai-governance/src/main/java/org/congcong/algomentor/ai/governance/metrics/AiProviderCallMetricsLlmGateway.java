package org.congcong.algomentor.ai.governance.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffortResolver;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

/** 对动态 provider dispatch 记录活跃调用和终态结果，不暴露实例或模型维度。 */
public final class AiProviderCallMetricsLlmGateway implements LlmGateway {

  public static final String CALLS_ACTIVE = "ai_provider_calls_active";
  public static final String CALLS_TOTAL = "ai_provider_calls_total";

  private static final String PROVIDER_TYPE_TAG = "provider_type";
  private static final String REASONING_EFFORT_TAG = "reasoning_effort";
  private static final String UNKNOWN_PROVIDER_TYPE = "unknown";
  private static final String PROVIDER_DEFAULT_REASONING_EFFORT = "provider_default";
  private final LlmGateway delegate;
  private final MeterRegistry meterRegistry;
  private final ConcurrentMap<CallDimensions, AtomicInteger> activeCalls = new ConcurrentHashMap<>();

  public AiProviderCallMetricsLlmGateway(LlmGateway delegate, MeterRegistry meterRegistry) {
    this.delegate = java.util.Objects.requireNonNull(delegate, "delegate must not be null");
    this.meterRegistry = java.util.Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
  }

  @Override
  public LlmCompletionResult complete(LlmCompletionRequest request) {
    CallObservation observation = start(request);
    try {
      LlmCompletionResult result = delegate.complete(request);
      observation.finish("success");
      return result;
    } catch (RuntimeException exception) {
      observation.finish("failure");
      throw exception;
    }
  }

  @Override
  public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
    return subscriber -> {
      CallObservation observation = start(request);
      try {
        delegate.stream(request).subscribe(new Flow.Subscriber<>() {
          @Override
          public void onSubscribe(Flow.Subscription subscription) {
            subscriber.onSubscribe(new Flow.Subscription() {
              @Override
              public void request(long count) {
                subscription.request(count);
              }

              @Override
              public void cancel() {
                subscription.cancel();
                observation.finish("cancelled");
              }
            });
          }

          @Override
          public void onNext(LlmStreamEvent event) {
            if (event instanceof LlmStreamEvent.Error) {
              observation.finish("failure");
            }
            subscriber.onNext(event);
          }

          @Override
          public void onError(Throwable throwable) {
            observation.finish("failure");
            subscriber.onError(throwable);
          }

          @Override
          public void onComplete() {
            observation.finish("success");
            subscriber.onComplete();
          }
        });
      } catch (RuntimeException exception) {
        observation.finish("failure");
        subscriber.onSubscribe(EmptySubscription.INSTANCE);
        subscriber.onError(exception);
      }
    };
  }

  private CallObservation start(LlmCompletionRequest request) {
    CallDimensions dimensions = new CallDimensions(providerType(request), reasoningEffort(request));
    AtomicInteger active = activeCalls.computeIfAbsent(dimensions, this::registerActiveGauge);
    active.incrementAndGet();
    return new CallObservation(dimensions, active);
  }

  private AtomicInteger registerActiveGauge(CallDimensions dimensions) {
    AtomicInteger active = new AtomicInteger();
    Gauge.builder(CALLS_ACTIVE, active, AtomicInteger::get)
        .tag(PROVIDER_TYPE_TAG, dimensions.providerType())
        .tag(REASONING_EFFORT_TAG, dimensions.reasoningEffort())
        .register(meterRegistry);
    return active;
  }

  private void recordTerminal(CallDimensions dimensions, String status) {
    Counter.builder(CALLS_TOTAL)
        .tag(PROVIDER_TYPE_TAG, dimensions.providerType())
        .tag(REASONING_EFFORT_TAG, dimensions.reasoningEffort())
        .tag("status", status)
        .register(meterRegistry)
        .increment();
  }

  private static String providerType(LlmCompletionRequest request) {
    if (request == null) {
      return UNKNOWN_PROVIDER_TYPE;
    }
    LlmInvocationTarget target = request.invocationTarget();
    if (target != null) {
      return target.providerType().value();
    }
    return request.modelSelector().providerId().map(value -> value.value()).orElse(UNKNOWN_PROVIDER_TYPE);
  }

  private static String reasoningEffort(LlmCompletionRequest request) {
    if (request == null) {
      return PROVIDER_DEFAULT_REASONING_EFFORT;
    }
    LlmReasoningEffort effort = LlmReasoningEffortResolver.resolve(request);
    return effort == null ? PROVIDER_DEFAULT_REASONING_EFFORT : effort.wireValue();
  }

  private final class CallObservation {

    private final CallDimensions dimensions;
    private final AtomicInteger active;
    private final AtomicBoolean finished = new AtomicBoolean();

    private CallObservation(CallDimensions dimensions, AtomicInteger active) {
      this.dimensions = dimensions;
      this.active = active;
    }

    private void finish(String status) {
      if (finished.compareAndSet(false, true)) {
        active.decrementAndGet();
        recordTerminal(dimensions, status);
      }
    }
  }

  private record CallDimensions(String providerType, String reasoningEffort) {
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
