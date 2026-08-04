package org.congcong.algomentor.ai.governance.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
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
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class AiProviderCallMetricsLlmGatewayTest {

  private static final LlmProviderType OPENAI = LlmProviderType.of("openai");
  private static final LlmModelId MODEL = LlmModelId.of("gpt-test");

  @Test
  void recordsSuccessfulAndFailedCompletionsByProviderType() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    CompletionGateway delegate = new CompletionGateway();
    AiProviderCallMetricsLlmGateway gateway = new AiProviderCallMetricsLlmGateway(delegate, registry);

    gateway.complete(request());
    delegate.fail = true;
    assertThatThrownBy(() -> gateway.complete(request())).isInstanceOf(IllegalStateException.class);

    assertThat(registry.get(AiProviderCallMetricsLlmGateway.CALLS_ACTIVE)
        .tag("provider_type", "openai").gauge().value()).isZero();
    assertThat(registry.get(AiProviderCallMetricsLlmGateway.CALLS_TOTAL)
        .tags("provider_type", "openai", "reasoning_effort", "provider_default", "status", "success")
        .counter().count()).isEqualTo(1d);
    assertThat(registry.get(AiProviderCallMetricsLlmGateway.CALLS_TOTAL)
        .tags("provider_type", "openai", "reasoning_effort", "provider_default", "status", "failure")
        .counter().count()).isEqualTo(1d);
  }

  @Test
  void recordsTheResolvedEffortWithAFixedLowCardinalityTag() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AiProviderCallMetricsLlmGateway gateway = new AiProviderCallMetricsLlmGateway(new CompletionGateway(), registry);

    gateway.complete(request(LlmReasoningEffort.NONE, LlmReasoningEffort.HIGH));
    gateway.complete(request(null, LlmReasoningEffort.HIGH));

    assertThat(registry.get(AiProviderCallMetricsLlmGateway.CALLS_TOTAL)
        .tags("provider_type", "openai", "reasoning_effort", "none", "status", "success")
        .counter().count()).isEqualTo(1d);
    assertThat(registry.get(AiProviderCallMetricsLlmGateway.CALLS_TOTAL)
        .tags("provider_type", "openai", "reasoning_effort", "high", "status", "success")
        .counter().count()).isEqualTo(1d);
  }

  @Test
  void streamCancellationSettlesTheActiveGaugeAndRecordsCancellation() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AiProviderCallMetricsLlmGateway gateway = new AiProviderCallMetricsLlmGateway(
        new PendingStreamGateway(), registry);
    AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

    gateway.stream(request()).subscribe(new Flow.Subscriber<>() {
      @Override
      public void onSubscribe(Flow.Subscription value) {
        subscription.set(value);
      }

      @Override
      public void onNext(LlmStreamEvent item) {
      }

      @Override
      public void onError(Throwable throwable) {
        throw new AssertionError(throwable);
      }

      @Override
      public void onComplete() {
      }
    });

    assertThat(subscription.get()).isNotNull();
    assertThat(registry.get(AiProviderCallMetricsLlmGateway.CALLS_ACTIVE)
        .tag("provider_type", "openai").gauge().value()).isEqualTo(1d);

    subscription.get().cancel();

    assertThat(registry.get(AiProviderCallMetricsLlmGateway.CALLS_ACTIVE)
        .tag("provider_type", "openai").gauge().value()).isZero();
    assertThat(registry.get(AiProviderCallMetricsLlmGateway.CALLS_TOTAL)
        .tags("provider_type", "openai", "status", "cancelled").counter().count()).isEqualTo(1d);
  }

  private static LlmCompletionRequest request() {
    return request(null, null);
  }

  private static LlmCompletionRequest request(
      LlmReasoningEffort requestReasoningEffort,
      LlmReasoningEffort routeReasoningEffort
  ) {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.requiring(Set.of()))
        .messages(List.of(LlmMessage.user("hello")))
        .invocationTarget(new LlmInvocationTarget(
            OPENAI,
            11L,
            17L,
            MODEL,
            Instant.parse("2026-07-27T00:00:00Z"),
            Set.of(LlmCapability.CHAT_COMPLETION, LlmCapability.REASONING_EFFORT),
            new NoopClient(),
            routeReasoningEffort))
        .options(LlmGenerationOptions.defaults().withReasoningEffort(requestReasoningEffort))
        .build();
  }

  private static final class CompletionGateway implements LlmGateway {

    private boolean fail;

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      if (fail) {
        throw new IllegalStateException("provider failed");
      }
      return result();
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class PendingStreamGateway implements LlmGateway {

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
        @Override
        public void request(long count) {
        }

        @Override
        public void cancel() {
        }
      });
    }
  }

  private static final class NoopClient implements LlmProviderClient {

    @Override
    public LlmCompletionResult complete(LlmModelId upstreamModelId, LlmCompletionRequest request) {
      return result();
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmModelId upstreamModelId, LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }
  }

  private static LlmCompletionResult result() {
    return new LlmCompletionResult(
        LlmMessage.assistant("ok"),
        List.of(),
        JsonNodeFactory.instance.nullNode(),
        LlmFinishReason.STOP,
        LlmUsage.empty(),
        LlmProviderId.of("openai"),
        MODEL,
        java.util.Map.of());
  }
}
