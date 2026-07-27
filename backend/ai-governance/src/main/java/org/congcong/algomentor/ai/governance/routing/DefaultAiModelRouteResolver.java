package org.congcong.algomentor.ai.governance.routing;

import java.util.Objects;
import org.congcong.algomentor.ai.governance.metrics.AiModelRouteMetrics;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.provider.model.AiConfiguredModel;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;
import org.congcong.algomentor.ai.governance.provider.repository.AiConfiguredModelRepository;
import org.congcong.algomentor.ai.governance.provider.repository.AiProviderInstanceRepository;
import org.congcong.algomentor.ai.governance.provider.runtime.ProviderClientHandle;
import org.congcong.algomentor.ai.governance.provider.runtime.ProviderClientRegistry;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapter;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapterRegistry;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.policy.model.ResolvedPolicy;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;

/** 不缓存数据库路由、模型或实例记录的默认模型路由解析器。 */
public class DefaultAiModelRouteResolver implements AiModelRouteResolver {

  private final GenericPolicyQueryService policyQueryService;
  private final AiModelRoutePolicyTypeContributor typeContributor;
  private final AiConfiguredModelRepository modelRepository;
  private final AiProviderInstanceRepository providerRepository;
  private final LlmProviderAdapterRegistry adapterRegistry;
  private final ProviderClientRegistry clientRegistry;
  private final AiModelRouteMetrics metrics;

  public DefaultAiModelRouteResolver(
      GenericPolicyQueryService policyQueryService,
      AiModelRoutePolicyTypeContributor typeContributor,
      AiConfiguredModelRepository modelRepository,
      AiProviderInstanceRepository providerRepository,
      LlmProviderAdapterRegistry adapterRegistry,
      ProviderClientRegistry clientRegistry
  ) {
    this(
        policyQueryService,
        typeContributor,
        modelRepository,
        providerRepository,
        adapterRegistry,
        clientRegistry,
        new AiModelRouteMetrics(null));
  }

  public DefaultAiModelRouteResolver(
      GenericPolicyQueryService policyQueryService,
      AiModelRoutePolicyTypeContributor typeContributor,
      AiConfiguredModelRepository modelRepository,
      AiProviderInstanceRepository providerRepository,
      LlmProviderAdapterRegistry adapterRegistry,
      ProviderClientRegistry clientRegistry,
      AiModelRouteMetrics metrics
  ) {
    this.policyQueryService = Objects.requireNonNull(policyQueryService, "policyQueryService must not be null");
    this.typeContributor = Objects.requireNonNull(typeContributor, "typeContributor must not be null");
    this.modelRepository = Objects.requireNonNull(modelRepository, "modelRepository must not be null");
    this.providerRepository = Objects.requireNonNull(providerRepository, "providerRepository must not be null");
    this.adapterRegistry = Objects.requireNonNull(adapterRegistry, "adapterRegistry must not be null");
    this.clientRegistry = Objects.requireNonNull(clientRegistry, "clientRegistry must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
  }

  @Override
  public ResolvedAiModelSnapshot resolve(AiBusinessScenario scenario, long userId) {
    if (scenario == null || userId < 1) {
      throw new IllegalArgumentException("AI business scenario and userId are required");
    }
    long startedAtNanos = System.nanoTime();
    try {
      ResolvedPolicy<AiModelRoutePolicyContent> policy = policyQueryService
          .resolve(typeContributor.require(scenario), userId)
          .orElseThrow(() -> routeNotConfigured());
      AiConfiguredModel model = modelRepository.findById(policy.content().modelId())
          .filter(AiConfiguredModel::enabled)
          .orElseThrow(() -> modelUnavailable());
      AiProviderInstance provider = providerRepository.findById(model.providerInstanceId())
          .filter(AiProviderInstance::enabled)
          .orElseThrow(() -> modelUnavailable());
      LlmProviderAdapter adapter = adapter(provider.providerType());
      ProviderClientHandle handle;
      try {
        handle = clientRegistry.acquire(provider, adapter);
      } catch (AiModelRouteException exception) {
        throw exception;
      } catch (RuntimeException exception) {
        throw new AiModelRouteException(
            AiGovernanceErrorCode.AI_PROVIDER_CONFIG_INVALID,
            "AI provider configuration is unavailable.",
            exception);
      }
      ResolvedAiModelSnapshot snapshot = new ResolvedAiModelSnapshot(
          scenario,
          policy.policyId(),
          policy.version(),
          policy.matchSource(),
          policy.matchedSubjectId(),
          model.id(),
          model.upstreamModelId(),
          provider.id(),
          provider.providerType(),
          provider.updatedAt(),
          handle.client(),
          adapter.supportedCapabilities());
      metrics.record(scenario, "success", startedAtNanos);
      return snapshot;
    } catch (AiModelRouteException exception) {
      metrics.record(scenario, exception.code().name(), startedAtNanos);
      throw exception;
    } catch (RuntimeException exception) {
      metrics.record(scenario, "failure", startedAtNanos);
      throw exception;
    }
  }

  private LlmProviderAdapter adapter(String providerType) {
    try {
      return adapterRegistry.find(LlmProviderType.of(providerType))
          .orElseThrow(() -> new AiModelRouteException(
              AiGovernanceErrorCode.AI_PROVIDER_TYPE_NOT_SUPPORTED,
              "AI provider type is unavailable."));
    } catch (AiModelRouteException exception) {
      throw exception;
    } catch (IllegalArgumentException exception) {
      throw new AiModelRouteException(
          AiGovernanceErrorCode.AI_PROVIDER_TYPE_NOT_SUPPORTED,
          "AI provider type is unavailable.",
          exception);
    }
  }

  private static AiModelRouteException routeNotConfigured() {
    return new AiModelRouteException(
        AiGovernanceErrorCode.AI_MODEL_ROUTE_NOT_CONFIGURED,
        "No AI model route is configured for this request.");
  }

  private static AiModelRouteException modelUnavailable() {
    return new AiModelRouteException(
        AiGovernanceErrorCode.AI_MODEL_UNAVAILABLE,
        "The configured AI model is unavailable.");
  }
}
