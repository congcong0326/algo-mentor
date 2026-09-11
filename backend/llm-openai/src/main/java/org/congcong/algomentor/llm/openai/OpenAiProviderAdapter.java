package org.congcong.algomentor.llm.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapter;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderInstanceSpec;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.openai.compatible.OpenAiCompatibleProviderClient;
import org.congcong.algomentor.llm.openai.compatible.OpenAiCompatibleResponsesClient;
import org.congcong.algomentor.llm.openai.compatible.OpenAiCompatibleResponsesClientFactory;

/** OpenAI Responses API 的唯一动态 provider adapter。 */
public final class OpenAiProviderAdapter implements LlmProviderAdapter {

  public static final LlmProviderType PROVIDER_TYPE = OpenAiProviderProfile.PROVIDER_TYPE;

  private final OpenAiCompatibleResponsesClientFactory clientFactory;

  public OpenAiProviderAdapter() {
    this(OpenAiCompatibleResponsesClient::fromConnectionConfig);
  }

  public OpenAiProviderAdapter(OpenAiCompatibleResponsesClientFactory clientFactory) {
    this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory must not be null");
  }

  @Override
  public LlmProviderType providerType() {
    return PROVIDER_TYPE;
  }

  @Override
  public String displayName() {
    return OpenAiProviderProfile.INSTANCE.displayName();
  }

  @Override
  public Set<LlmCapability> supportedCapabilities() {
    return OpenAiProviderProfile.INSTANCE.supportedCapabilities();
  }

  @Override
  public Set<LlmReasoningEffort> acceptedReasoningEfforts() {
    return OpenAiProviderProfile.INSTANCE.acceptedReasoningEfforts();
  }

  @Override
  public JsonNode defaultConfig() {
    return JsonNodeFactory.instance.objectNode()
        .put("apiKey", "")
        .put("baseUrl", "https://api.openai.com/v1")
        .put("timeoutSeconds", 300)
        .put("maxRetries", 2);
  }

  @Override
  public void validateConfig(JsonNode config) {
    OpenAiProviderConfig.fromJson(config);
  }

  @Override
  public LlmProviderClient createClient(LlmProviderInstanceSpec instance) {
    if (instance == null || !PROVIDER_TYPE.equals(instance.providerType())) {
      throw new IllegalArgumentException("OpenAI adapter requires an openai provider instance spec");
    }
    OpenAiProviderConfig config = OpenAiProviderConfig.fromJson(instance.config());
    return new OpenAiCompatibleProviderClient(
        clientFactory.create(config.toConnectionConfig()),
        OpenAiProviderProfile.INSTANCE,
        config.maxRetries());
  }
}
