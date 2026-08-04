package org.congcong.algomentor.ai.governance.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.metrics.AiModelRouteMetrics;
import org.congcong.algomentor.ai.governance.provider.model.AiConfiguredModel;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;
import org.congcong.algomentor.ai.governance.provider.repository.AiConfiguredModelRepository;
import org.congcong.algomentor.ai.governance.provider.repository.AiProviderInstanceRepository;
import org.congcong.algomentor.ai.governance.provider.runtime.ProviderClientRegistry;
import org.congcong.algomentor.ai.governance.provider.service.AiProviderManagementService;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapter;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapterRegistry;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderInstanceSpec;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.policy.model.PolicyMatchSource;
import org.congcong.algomentor.policy.model.ResolvedPolicy;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.junit.jupiter.api.Test;

class DefaultAiModelRouteResolverTest {

  @Test
  void resolvesEnabledRuleToLowSensitivitySnapshotAndVersionedClient() {
    Fixture fixture = new Fixture();

    ResolvedAiModelSnapshot snapshot = fixture.resolver.resolve(AiBusinessScenario.PRACTICE_CHAT, 7L);

    assertThat(snapshot.scenario()).isEqualTo(AiBusinessScenario.PRACTICE_CHAT);
    assertThat(snapshot.routePolicyId()).isEqualTo(17L);
    assertThat(snapshot.aiModelId()).isEqualTo(101L);
    assertThat(snapshot.providerInstanceId()).isEqualTo(11L);
    assertThat(snapshot.invocationTarget().upstreamModelId()).isEqualTo(LlmModelId.of("gpt-test"));
    assertThat(snapshot.routeReasoningEffort()).isEqualTo(LlmReasoningEffort.HIGH);
    assertThat(snapshot.invocationTarget().routeReasoningEffort()).isEqualTo(LlmReasoningEffort.HIGH);
    assertThat(snapshot.trustedMetadata())
        .containsEntry("aiConfiguredModelId", 101L)
        .containsEntry("aiProviderInstanceId", 11L)
        .containsEntry("aiRouteReasoningEffort", "high")
        .doesNotContainKey("apiKey")
        .doesNotContainValue("secret-key");
    assertThat(fixture.adapter.creations).isOne();

    fixture.policy.set(new ResolvedPolicy<>(17L, "ai.model-route.practice-chat.v1", "Practice route", 3,
        new AiModelRoutePolicyContent(101L, LlmReasoningEffort.NONE), PolicyMatchSource.GROUP, 9L, 3L));
    assertThat(snapshot.invocationTarget().routeReasoningEffort()).isEqualTo(LlmReasoningEffort.HIGH);
  }

  @Test
  void failsWithoutFallbackWhenNoRuleMatches() {
    Fixture fixture = new Fixture();
    fixture.policy.set(null);

    AiModelRouteException exception = catchThrowableOfType(
        () -> fixture.resolver.resolve(AiBusinessScenario.PRACTICE_CHAT, 7L),
        AiModelRouteException.class);

    assertThat(exception.code()).isEqualTo(AiGovernanceErrorCode.AI_MODEL_ROUTE_NOT_CONFIGURED);
    assertThat(fixture.adapter.creations).isZero();
  }

  @Test
  void failsWhenConfiguredModelIsDisabled() {
    Fixture fixture = new Fixture();
    when(fixture.models.findById(101L)).thenReturn(Optional.of(fixture.model(false)));

    AiModelRouteException exception = catchThrowableOfType(
        () -> fixture.resolver.resolve(AiBusinessScenario.PRACTICE_CHAT, 7L),
        AiModelRouteException.class);

    assertThat(exception.code()).isEqualTo(AiGovernanceErrorCode.AI_MODEL_UNAVAILABLE);
    assertThat(fixture.adapter.creations).isZero();
  }

  @Test
  void failsForUnsupportedTypeAndInvalidProviderConfig() {
    Fixture unsupported = new Fixture();
    when(unsupported.providers.findById(11L)).thenReturn(Optional.of(unsupported.provider("other", config("secret-key"))));

    AiModelRouteException unsupportedError = catchThrowableOfType(
        () -> unsupported.resolver.resolve(AiBusinessScenario.PRACTICE_CHAT, 7L),
        AiModelRouteException.class);
    assertThat(unsupportedError.code()).isEqualTo(AiGovernanceErrorCode.AI_PROVIDER_TYPE_NOT_SUPPORTED);

    Fixture invalid = new Fixture();
    when(invalid.providers.findById(11L)).thenReturn(Optional.of(invalid.provider("openai", JsonNodeFactory.instance.objectNode())));

    AiModelRouteException invalidError = catchThrowableOfType(
        () -> invalid.resolver.resolve(AiBusinessScenario.PRACTICE_CHAT, 7L),
        AiModelRouteException.class);
    assertThat(invalidError.code()).isEqualTo(AiGovernanceErrorCode.AI_PROVIDER_CONFIG_INVALID);
  }

  @Test
  void recordsSuccessfulAndRejectedRouteResolutionsWithoutSensitiveTags() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    Fixture fixture = new Fixture(new AiModelRouteMetrics(registry));

    fixture.resolver.resolve(AiBusinessScenario.PRACTICE_CHAT, 7L);
    fixture.policy.set(null);
    catchThrowableOfType(
        () -> fixture.resolver.resolve(AiBusinessScenario.PRACTICE_CHAT, 7L),
        AiModelRouteException.class);

    assertThat(registry.get(AiModelRouteMetrics.RESOLUTIONS_TOTAL)
        .tags("scenario", "practice-chat", "result", "success").counter().count()).isEqualTo(1d);
    assertThat(registry.get(AiModelRouteMetrics.RESOLUTIONS_TOTAL)
        .tags("scenario", "practice-chat", "result", "AI_MODEL_ROUTE_NOT_CONFIGURED")
        .counter().count()).isEqualTo(1d);
    assertThat(registry.get(AiModelRouteMetrics.RESOLUTION_SECONDS)
        .tag("scenario", "practice-chat").timer().count()).isEqualTo(2L);
  }

  private static JsonNode config(String apiKey) {
    return JsonNodeFactory.instance.objectNode().put("apiKey", apiKey);
  }

  private static final class Fixture {
    private final AtomicReference<ResolvedPolicy<AiModelRoutePolicyContent>> policy = new AtomicReference<>(
        new ResolvedPolicy<>(17L, "ai.model-route.practice-chat.v1", "Practice route", 2,
            new AiModelRoutePolicyContent(101L, LlmReasoningEffort.HIGH), PolicyMatchSource.GROUP, 9L, 3L));
    private final AiConfiguredModelRepository models = mock(AiConfiguredModelRepository.class);
    private final AiProviderInstanceRepository providers = mock(AiProviderInstanceRepository.class);
    private final RecordingAdapter adapter = new RecordingAdapter();
    private final DefaultAiModelRouteResolver resolver;

    private Fixture() {
      this(new AiModelRouteMetrics(null));
    }

    private Fixture(AiModelRouteMetrics metrics) {
      GenericPolicyQueryService policies = new GenericPolicyQueryService() {
        @Override
        @SuppressWarnings("unchecked")
        public <T> Optional<ResolvedPolicy<T>> resolve(GenericPolicyType<T> type, long userId) {
          return Optional.ofNullable((ResolvedPolicy<T>) policy.get());
        }
      };
      AiModelRoutePolicyTypeContributor types = new AiModelRoutePolicyTypeContributor(
          mock(AiProviderManagementService.class));
      when(models.findById(101L)).thenReturn(Optional.of(model(true)));
      when(providers.findById(11L)).thenReturn(Optional.of(provider("openai", config("secret-key"))));
      resolver = new DefaultAiModelRouteResolver(
          policies,
          types,
          models,
          providers,
          new LlmProviderAdapterRegistry(List.of(adapter)),
          new ProviderClientRegistry(),
          metrics);
    }

    private AiConfiguredModel model(boolean enabled) {
      return new AiConfiguredModel(101L, 11L, "Test model", "gpt-test", enabled,
          Instant.parse("2026-07-27T00:00:00Z"), Instant.parse("2026-07-27T00:00:00Z"));
    }

    private AiProviderInstance provider(String type, JsonNode config) {
      return new AiProviderInstance(11L, "Test provider", type, true, config,
          Instant.parse("2026-07-27T00:00:00Z"), Instant.parse("2026-07-27T00:00:00Z"));
    }
  }

  private static final class RecordingAdapter implements LlmProviderAdapter {
    private int creations;

    @Override
    public LlmProviderType providerType() {
      return LlmProviderType.of("openai");
    }

    @Override
    public String displayName() {
      return "OpenAI";
    }

    @Override
    public Set<LlmCapability> supportedCapabilities() {
      return Set.of(LlmCapability.CHAT_COMPLETION);
    }

    @Override
    public void validateConfig(JsonNode config) {
      if (!config.path("apiKey").isTextual() || config.path("apiKey").asText().isBlank()) {
        throw new IllegalArgumentException("apiKey is required");
      }
    }

    @Override
    public LlmProviderClient createClient(LlmProviderInstanceSpec instance) {
      creations++;
      return new LlmProviderClient() {
        @Override
        public LlmCompletionResult complete(LlmModelId upstreamModelId, LlmCompletionRequest request) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Flow.Publisher<LlmStreamEvent> stream(LlmModelId upstreamModelId, LlmCompletionRequest request) {
          throw new UnsupportedOperationException();
        }
      };
    }
  }
}
