package org.congcong.algomentor.ai.governance.provider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.congcong.algomentor.ai.governance.model.AiGovernanceAdminException;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.provider.model.AiConfiguredModel;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;
import org.congcong.algomentor.ai.governance.provider.repository.AiConfiguredModelRepository;
import org.congcong.algomentor.ai.governance.provider.repository.AiProviderInstanceRepository;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapter;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapterRegistry;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;

/** 管理员维护 provider instance 与配置模型的应用服务，不进行远程连接测试。 */
public class AiProviderManagementService {

  private final AiProviderInstanceRepository providerRepository;
  private final AiConfiguredModelRepository modelRepository;
  private final LlmProviderAdapterRegistry adapterRegistry;
  private final Clock clock;

  public AiProviderManagementService(
      AiProviderInstanceRepository providerRepository,
      AiConfiguredModelRepository modelRepository,
      LlmProviderAdapterRegistry adapterRegistry,
      Clock clock
  ) {
    this.providerRepository = Objects.requireNonNull(providerRepository, "providerRepository must not be null");
    this.modelRepository = Objects.requireNonNull(modelRepository, "modelRepository must not be null");
    this.adapterRegistry = Objects.requireNonNull(adapterRegistry, "adapterRegistry must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public List<AiProviderInstance> listProviders() {
    return providerRepository.findAll();
  }

  public AiProviderInstance getProvider(long providerInstanceId) {
    return requireProvider(providerInstanceId);
  }

  public AiProviderInstance createProvider(
      String name,
      String providerType,
      boolean enabled,
      JsonNode config
  ) {
    String normalizedType = normalizeProviderType(providerType);
    validateConfig(normalizedType, config, false);
    try {
      if (providerRepository.findByName(normalizeName(name)).isPresent()) {
        throw nameAlreadyExists();
      }
      Instant now = Instant.now(clock);
      return providerRepository.insert(new AiProviderInstance(
          null, name, normalizedType, enabled, config, now, now));
    } catch (AiGovernanceAdminException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      if (isLikelyUniqueViolation(exception)) {
        throw nameAlreadyExists();
      }
      throw invalidProvider("Failed to create AI provider instance.", exception);
    }
  }

  public AiProviderInstance updateProvider(
      long providerInstanceId,
      String name,
      boolean enabled,
      JsonNode config
  ) {
    AiProviderInstance existing = requireProvider(providerInstanceId);
    validateConfig(existing.providerType(), config, false);
    try {
      providerRepository.findByName(normalizeName(name))
          .filter(found -> !found.id().equals(providerInstanceId))
          .ifPresent(found -> {
            throw nameAlreadyExists();
          });
      AiProviderInstance updated = new AiProviderInstance(
          providerInstanceId,
          name,
          existing.providerType(),
          enabled,
          config,
          existing.createdAt(),
          nextUpdatedAt(existing.updatedAt()));
      if (!providerRepository.update(updated)) {
        throw providerNotFound();
      }
      return requireProvider(providerInstanceId);
    } catch (AiGovernanceAdminException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      if (isLikelyUniqueViolation(exception)) {
        throw nameAlreadyExists();
      }
      throw invalidProvider("Failed to update AI provider instance.", exception);
    }
  }

  public List<AiConfiguredModel> listModels(long providerInstanceId) {
    requireProvider(providerInstanceId);
    return modelRepository.findByProviderInstanceId(providerInstanceId);
  }

  public AiConfiguredModel getModel(long modelId) {
    if (modelId < 1) {
      throw modelNotFound();
    }
    return modelRepository.findById(modelId).orElseThrow(AiProviderManagementService::modelNotFound);
  }

  public AiConfiguredModel createModel(
      long providerInstanceId,
      String displayName,
      String upstreamModelId,
      boolean enabled
  ) {
    requireProvider(providerInstanceId);
    try {
      String normalizedModelId = normalizeModelId(upstreamModelId);
      if (modelRepository.findByProviderInstanceIdAndUpstreamModelId(providerInstanceId, normalizedModelId)
          .isPresent()) {
        throw modelAlreadyExists();
      }
      Instant now = Instant.now(clock);
      return modelRepository.insert(new AiConfiguredModel(
          null, providerInstanceId, displayName, normalizedModelId, enabled, now, now));
    } catch (AiGovernanceAdminException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      if (isLikelyUniqueViolation(exception)) {
        throw modelAlreadyExists();
      }
      throw invalidModel("Failed to create configured AI model.", exception);
    }
  }

  public AiConfiguredModel updateModel(
      long modelId,
      String displayName,
      String upstreamModelId,
      boolean enabled
  ) {
    AiConfiguredModel existing = getModel(modelId);
    try {
      String normalizedModelId = normalizeModelId(upstreamModelId);
      modelRepository.findByProviderInstanceIdAndUpstreamModelId(
              existing.providerInstanceId(), normalizedModelId)
          .filter(found -> !found.id().equals(modelId))
          .ifPresent(found -> {
            throw modelAlreadyExists();
          });
      AiConfiguredModel updated = new AiConfiguredModel(
          modelId,
          existing.providerInstanceId(),
          displayName,
          normalizedModelId,
          enabled,
          existing.createdAt(),
          nextUpdatedAt(existing.updatedAt()));
      if (!modelRepository.update(updated)) {
        throw modelNotFound();
      }
      return getModel(modelId);
    } catch (AiGovernanceAdminException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      if (isLikelyUniqueViolation(exception)) {
        throw modelAlreadyExists();
      }
      throw invalidModel("Failed to update configured AI model.", exception);
    }
  }

  public List<AiProviderTypeDescriptor> supportedProviderTypes() {
    return adapterRegistry.adapters().entrySet().stream()
        .map(entry -> descriptor(entry.getKey().value(), entry.getValue()))
        .sorted(Comparator.comparing(AiProviderTypeDescriptor::code))
        .toList();
  }

  /** 路由策略保存时复用同一校验，允许模型或实例处于停用状态。 */
  public void validateModelReference(long modelId) {
    validateModelRoute(modelId, null);
  }

  /** 校验路由目标与可选 reasoning effort 的 provider 协议兼容性。 */
  public void validateModelRoute(long modelId, LlmReasoningEffort reasoningEffort) {
    AiConfiguredModel model = getModel(modelId);
    AiProviderInstance provider = requireProvider(model.providerInstanceId());
    LlmProviderAdapter adapter = requireAdapter(provider.providerType(), false);
    if (reasoningEffort == null) {
      return;
    }
    if (!adapter.supportedCapabilities().contains(org.congcong.algomentor.llm.core.provider.LlmCapability.REASONING_EFFORT)
        || !adapter.acceptedReasoningEfforts().contains(reasoningEffort)) {
      throw invalidModel("Configured AI provider does not accept the requested reasoning effort.", null);
    }
  }

  private static AiProviderTypeDescriptor descriptor(String code, LlmProviderAdapter adapter) {
    List<LlmReasoningEffort> efforts = java.util.Arrays.stream(LlmReasoningEffort.values())
        .filter(adapter.acceptedReasoningEfforts()::contains)
        .toList();
    return new AiProviderTypeDescriptor(code, adapter.displayName(), efforts, adapter.defaultConfig());
  }

  private AiProviderInstance requireProvider(long providerInstanceId) {
    if (providerInstanceId < 1) {
      throw providerNotFound();
    }
    return providerRepository.findById(providerInstanceId).orElseThrow(AiProviderManagementService::providerNotFound);
  }

  private String normalizeProviderType(String providerType) {
    try {
      return LlmProviderType.of(providerType).value();
    } catch (IllegalArgumentException exception) {
      throw unsupportedProviderType(exception);
    }
  }

  private void validateConfig(String providerType, JsonNode config, boolean runtime) {
    LlmProviderAdapter adapter = requireAdapter(providerType, runtime);
    try {
      adapter.validateConfig(config);
    } catch (IllegalArgumentException exception) {
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_PROVIDER_CONFIG_INVALID,
          runtime ? "AI provider configuration is unavailable." : "AI provider configuration is invalid.",
          exception);
    }
  }

  private LlmProviderAdapter requireAdapter(String providerType, boolean runtime) {
    try {
      return adapterRegistry.find(LlmProviderType.of(providerType))
          .orElseThrow(() -> unsupportedProviderType(null));
    } catch (AiGovernanceAdminException exception) {
      throw exception;
    } catch (IllegalArgumentException exception) {
      throw unsupportedProviderType(exception);
    }
  }

  private Instant nextUpdatedAt(Instant current) {
    Instant now = Instant.now(clock);
    return current != null && !now.isAfter(current) ? current.plusNanos(1) : now;
  }

  private static String normalizeName(String value) {
    if (value == null || value.isBlank()) {
      throw invalidProvider("AI provider instance is invalid.", null);
    }
    return value.trim();
  }

  private static String normalizeModelId(String value) {
    if (value == null || value.isBlank()) {
      throw invalidModel("Configured AI model is invalid.", null);
    }
    return value.trim();
  }

  private static AiGovernanceAdminException unsupportedProviderType(Throwable cause) {
    return new AiGovernanceAdminException(
        AiGovernanceErrorCode.AI_PROVIDER_TYPE_NOT_SUPPORTED,
        "AI provider type is not supported.", cause);
  }

  private static AiGovernanceAdminException nameAlreadyExists() {
    return new AiGovernanceAdminException(
        AiGovernanceErrorCode.AI_PROVIDER_NAME_ALREADY_EXISTS,
        "AI provider instance name already exists.");
  }

  private static AiGovernanceAdminException modelAlreadyExists() {
    return new AiGovernanceAdminException(
        AiGovernanceErrorCode.AI_MODEL_ALREADY_EXISTS,
        "Configured AI model already exists for this provider instance.");
  }

  private static AiGovernanceAdminException providerNotFound() {
    return new AiGovernanceAdminException(
        AiGovernanceErrorCode.AI_PROVIDER_NOT_FOUND,
        "AI provider instance was not found.");
  }

  private static AiGovernanceAdminException modelNotFound() {
    return new AiGovernanceAdminException(
        AiGovernanceErrorCode.AI_MODEL_NOT_FOUND,
        "Configured AI model was not found.");
  }

  private static AiGovernanceAdminException invalidProvider(String message, Throwable cause) {
    return new AiGovernanceAdminException(AiGovernanceErrorCode.AI_PROVIDER_CONFIG_INVALID, message, cause);
  }

  private static AiGovernanceAdminException invalidModel(String message, Throwable cause) {
    return new AiGovernanceAdminException(AiGovernanceErrorCode.AI_MODEL_INVALID, message, cause);
  }

  private static boolean isLikelyUniqueViolation(RuntimeException exception) {
    String message = exception.getMessage();
    return message != null && message.toLowerCase(Locale.ROOT).contains("duplicate");
  }

  public record AiProviderTypeDescriptor(
      String code,
      String displayName,
      List<LlmReasoningEffort> reasoningEfforts,
      JsonNode defaultConfig
  ) {
    public AiProviderTypeDescriptor(String code, String displayName) {
      this(code, displayName, List.of(), JsonNodeFactory.instance.objectNode());
    }

    public AiProviderTypeDescriptor {
      code = Objects.requireNonNull(code, "code must not be null");
      displayName = Objects.requireNonNull(displayName, "displayName must not be null");
      reasoningEfforts = reasoningEfforts == null ? List.of() : List.copyOf(reasoningEfforts);
      defaultConfig = defaultConfig == null ? JsonNodeFactory.instance.objectNode() : defaultConfig.deepCopy();
    }

    @Override
    public JsonNode defaultConfig() {
      return defaultConfig.deepCopy();
    }
  }
}
