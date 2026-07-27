package org.congcong.algomentor.llm.openai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapter;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderInstanceSpec;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;

/** OpenAI Responses API 的唯一动态 provider adapter。 */
public final class OpenAiProviderAdapter implements LlmProviderAdapter {

  public static final LlmProviderType PROVIDER_TYPE = LlmProviderType.of("openai");
  private static final Set<LlmCapability> SUPPORTED_CAPABILITIES = Set.of(
      LlmCapability.CHAT_COMPLETION,
      LlmCapability.STREAMING,
      LlmCapability.TOOL_CALLING,
      LlmCapability.STRUCTURED_OUTPUT,
      LlmCapability.JSON_SCHEMA_OUTPUT,
      LlmCapability.TOKEN_USAGE,
      LlmCapability.CACHED_TOKEN_USAGE);

  private final OpenAiResponsesClientFactory clientFactory;

  public OpenAiProviderAdapter() {
    this(OpenAiResponsesClient::fromConfig);
  }

  public OpenAiProviderAdapter(OpenAiResponsesClientFactory clientFactory) {
    this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory must not be null");
  }

  @Override
  public LlmProviderType providerType() {
    return PROVIDER_TYPE;
  }

  @Override
  public String displayName() {
    return "OpenAI";
  }

  @Override
  public Set<LlmCapability> supportedCapabilities() {
    return SUPPORTED_CAPABILITIES;
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
    return new OpenAiProviderClient(clientFactory.create(config));
  }
}
