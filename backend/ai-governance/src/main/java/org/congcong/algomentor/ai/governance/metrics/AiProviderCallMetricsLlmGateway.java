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
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

/** 对动态 provider dispatch 记录活跃调用和终态结果，不暴露实例或模型维度。 */
public final class AiProviderCallMetricsLlmGateway implements LlmGateway {

  public static final String CALLS_ACTIVE = "ai_provider_calls_active";
  public static final String CALLS_TOTAL = "ai_provider_calls_total";

  private static final String UNKNOWN_PROVIDER_TYPE = "unknown";
  private final LlmGateway delegate;
  private final MeterRegistry meterRegistry;
  private final ConcurrentMap<String, AtomicInteger> activeCalls = new ConcurrentHashMap<>();

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
    String providerType = providerType(request);
    AtomicInteger active = activeCalls.computeIfAbsent(providerType, this::registerActiveGauge);
    active.incrementAndGet();
    return new CallObservation(providerType, active);
  }

  private AtomicInteger registerActiveGauge(String providerType) {
    AtomicInteger active = new AtomicInteger();
    Gauge.builder(CALLS_ACTIVE, active, AtomicInteger::get)
        .tag("provider_type", providerType)
        .register(meterRegistry);
    return active;
  }

  private void recordTerminal(String providerType, String status) {
    Counter.builder(CALLS_TOTAL)
        .tag("provider_type", providerType)
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

  private final class CallObservation {

    private final String providerType;
    private final AtomicInteger active;
    private final AtomicBoolean finished = new AtomicBoolean();

    private CallObservation(String providerType, AtomicInteger active) {
      this.providerType = providerType;
      this.active = active;
    }

    private void finish(String status) {
      if (finished.compareAndSet(false, true)) {
        active.decrementAndGet();
        recordTerminal(providerType, status);
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
