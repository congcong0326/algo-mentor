package org.congcong.algomentor.llm.deepseek;

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

/** DeepSeek Responses API 的动态 provider adapter。 */
public final class DeepSeekProviderAdapter implements LlmProviderAdapter {

  public static final LlmProviderType PROVIDER_TYPE = DeepSeekProviderProfile.PROVIDER_TYPE;

  private final OpenAiCompatibleResponsesClientFactory clientFactory;

  public DeepSeekProviderAdapter() {
    this(OpenAiCompatibleResponsesClient::fromConnectionConfig);
  }

  public DeepSeekProviderAdapter(OpenAiCompatibleResponsesClientFactory clientFactory) {
    this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory must not be null");
  }

  @Override
  public LlmProviderType providerType() {
    return PROVIDER_TYPE;
  }

  @Override
  public String displayName() {
    return DeepSeekProviderProfile.INSTANCE.displayName();
  }

  @Override
  public Set<LlmCapability> supportedCapabilities() {
    return DeepSeekProviderProfile.INSTANCE.supportedCapabilities();
  }

  @Override
  public Set<LlmReasoningEffort> acceptedReasoningEfforts() {
    return DeepSeekProviderProfile.INSTANCE.acceptedReasoningEfforts();
  }

  @Override
  public JsonNode defaultConfig() {
    return JsonNodeFactory.instance.objectNode()
        .put("apiKey", "")
        .put("baseUrl", "https://api.deepseek.com")
        .put("timeoutSeconds", 300)
        .put("maxRetries", 2);
  }

  @Override
  public void validateConfig(JsonNode config) {
    DeepSeekProviderConfig.fromJson(config);
  }

  @Override
  public LlmProviderClient createClient(LlmProviderInstanceSpec instance) {
    if (instance == null || !PROVIDER_TYPE.equals(instance.providerType())) {
      throw new IllegalArgumentException("DeepSeek adapter requires a deepseek provider instance spec");
    }
    DeepSeekProviderConfig config = DeepSeekProviderConfig.fromJson(instance.config());
    return new OpenAiCompatibleProviderClient(
        clientFactory.create(config.toConnectionConfig()),
        DeepSeekProviderProfile.INSTANCE);
  }
}
