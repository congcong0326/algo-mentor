package org.congcong.algomentor.ai.governance.provider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Flow;
import org.congcong.algomentor.ai.governance.model.AiGovernanceAdminException;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.provider.model.AiConfiguredModel;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;
import org.congcong.algomentor.ai.governance.provider.repository.AiConfiguredModelRepository;
import org.congcong.algomentor.ai.governance.provider.repository.AiProviderInstanceRepository;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapter;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapterRegistry;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderInstanceSpec;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class AiProviderManagementServiceTest {

  @Test
  void managesMultipleInstancesModelsAndImmutableProviderType() {
    Fixture fixture = new Fixture();
    AiProviderInstance primary = fixture.service.createProvider(
        "OpenAI primary", "OPENAI", true, config("key-one"));
    AiProviderInstance secondary = fixture.service.createProvider(
        "OpenAI secondary", "openai", false, config("key-two"));

    AiConfiguredModel firstModel = fixture.service.createModel(primary.id(), "Fast", "gpt-fast", true);
    AiConfiguredModel secondModel = fixture.service.createModel(secondary.id(), "Fast", "gpt-fast", false);
    AiProviderInstance updated = fixture.service.updateProvider(
        primary.id(), "OpenAI primary renamed", false, config("key-three"));

    assertThat(fixture.service.supportedProviderTypes())
        .containsExactly(new AiProviderManagementService.AiProviderTypeDescriptor("openai", "OpenAI"));
    assertThat(fixture.service.listProviders()).extracting(AiProviderInstance::id)
        .containsExactly(primary.id(), secondary.id());
    assertThat(firstModel.providerInstanceId()).isEqualTo(primary.id());
    assertThat(secondModel.providerInstanceId()).isEqualTo(secondary.id());
    assertThat(updated.providerType()).isEqualTo("openai");
    assertThat(updated.enabled()).isFalse();
    assertThat(updated.updatedAt()).isAfter(primary.updatedAt());
  }

  @Test
  void rejectsDuplicateAndInvalidResourcesWithoutRemoteCalls() {
    Fixture fixture = new Fixture();
    AiProviderInstance provider = fixture.service.createProvider("OpenAI", "openai", true, config("key"));
    fixture.service.createModel(provider.id(), "Fast", "gpt-fast", true);

    AiGovernanceAdminException duplicateName = catchThrowableOfType(
        () -> fixture.service.createProvider("OpenAI", "openai", true, config("different")),
        AiGovernanceAdminException.class);
    AiGovernanceAdminException duplicateModel = catchThrowableOfType(
        () -> fixture.service.createModel(provider.id(), "Other", "gpt-fast", true),
        AiGovernanceAdminException.class);
    AiGovernanceAdminException invalidConfig = catchThrowableOfType(
        () -> fixture.service.createProvider("Broken", "openai", true, JsonNodeFactory.instance.objectNode()),
        AiGovernanceAdminException.class);

    assertThat(duplicateName.code()).isEqualTo(AiGovernanceErrorCode.AI_PROVIDER_NAME_ALREADY_EXISTS);
    assertThat(duplicateModel.code()).isEqualTo(AiGovernanceErrorCode.AI_MODEL_ALREADY_EXISTS);
    assertThat(invalidConfig.code()).isEqualTo(AiGovernanceErrorCode.AI_PROVIDER_CONFIG_INVALID);
    assertThat(fixture.adapter.createdClients).isZero();
  }

  @Test
  void acceptsDisabledModelReferenceForPlannedRouteButRejectsUnknownModel() {
    Fixture fixture = new Fixture();
    AiProviderInstance provider = fixture.service.createProvider("OpenAI", "openai", false, config("key"));
    AiConfiguredModel model = fixture.service.createModel(provider.id(), "Fast", "gpt-fast", false);

    fixture.service.validateModelReference(model.id());

    AiGovernanceAdminException missing = catchThrowableOfType(
        () -> fixture.service.validateModelReference(999L),
        AiGovernanceAdminException.class);
    assertThat(missing.code()).isEqualTo(AiGovernanceErrorCode.AI_MODEL_NOT_FOUND);
  }

  private static JsonNode config(String apiKey) {
    return JsonNodeFactory.instance.objectNode()
        .put("apiKey", apiKey)
        .put("baseUrl", "https://api.openai.com/v1")
        .put("timeoutSeconds", 300)
        .put("maxRetries", 2);
  }

  private static final class Fixture {
    private final RecordingAdapter adapter = new RecordingAdapter();
    private final AiProviderManagementService service = new AiProviderManagementService(
        new InMemoryProviderRepository(),
        new InMemoryModelRepository(),
        new LlmProviderAdapterRegistry(List.of(adapter)),
        Clock.fixed(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC));
  }

  private static final class InMemoryProviderRepository implements AiProviderInstanceRepository {
    private final Map<Long, AiProviderInstance> instances = new LinkedHashMap<>();
    private long nextId = 1L;

    @Override
    public List<AiProviderInstance> findAll() {
      return instances.values().stream().sorted(Comparator.comparing(AiProviderInstance::name)).toList();
    }

    @Override
    public Optional<AiProviderInstance> findById(long id) {
      return Optional.ofNullable(instances.get(id));
    }

    @Override
    public Optional<AiProviderInstance> findByName(String name) {
      return instances.values().stream().filter(instance -> instance.name().equals(name)).findFirst();
    }

    @Override
    public AiProviderInstance insert(AiProviderInstance instance) {
      AiProviderInstance saved = new AiProviderInstance(
          nextId++, instance.name(), instance.providerType(), instance.enabled(), instance.config(),
          instance.createdAt(), instance.updatedAt());
      instances.put(saved.id(), saved);
      return saved;
    }

    @Override
    public boolean update(AiProviderInstance instance) {
      if (!instances.containsKey(instance.id())) return false;
      instances.put(instance.id(), instance);
      return true;
    }
  }

  private static final class InMemoryModelRepository implements AiConfiguredModelRepository {
    private final Map<Long, AiConfiguredModel> models = new LinkedHashMap<>();
    private long nextId = 1L;

    @Override
    public List<AiConfiguredModel> findByProviderInstanceId(long providerInstanceId) {
      return models.values().stream()
          .filter(model -> model.providerInstanceId() == providerInstanceId)
          .sorted(Comparator.comparing(AiConfiguredModel::displayName))
          .toList();
    }

    @Override
    public Optional<AiConfiguredModel> findById(long id) {
      return Optional.ofNullable(models.get(id));
    }

    @Override
    public Optional<AiConfiguredModel> findByProviderInstanceIdAndUpstreamModelId(
        long providerInstanceId,
        String upstreamModelId
    ) {
      return models.values().stream()
          .filter(model -> model.providerInstanceId() == providerInstanceId
              && model.upstreamModelId().equals(upstreamModelId))
          .findFirst();
    }

    @Override
    public AiConfiguredModel insert(AiConfiguredModel model) {
      AiConfiguredModel saved = new AiConfiguredModel(
          nextId++, model.providerInstanceId(), model.displayName(), model.upstreamModelId(), model.enabled(),
          model.createdAt(), model.updatedAt());
      models.put(saved.id(), saved);
      return saved;
    }

    @Override
    public boolean update(AiConfiguredModel model) {
      if (!models.containsKey(model.id())) return false;
      models.put(model.id(), model);
      return true;
    }
  }

  private static final class RecordingAdapter implements LlmProviderAdapter {
    private int createdClients;

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
      if (config == null || !config.path("apiKey").isTextual() || config.path("apiKey").asText().isBlank()) {
        throw new IllegalArgumentException("apiKey is required");
      }
    }

    @Override
    public LlmProviderClient createClient(LlmProviderInstanceSpec instance) {
      createdClients++;
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
