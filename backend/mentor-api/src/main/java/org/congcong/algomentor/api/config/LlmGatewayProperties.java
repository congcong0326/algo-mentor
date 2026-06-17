package org.congcong.algomentor.api.config;

import org.congcong.algomentor.llm.core.gateway.LlmGatewayOptions;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用层选择默认 LLM provider/model 的配置。
 */
@ConfigurationProperties(prefix = MentorConfigurationKeys.AI_GATEWAY_PREFIX)
public class LlmGatewayProperties {

  private String defaultProvider = "openai";
  private String defaultModel = "gpt-5.2";

  public String getDefaultProvider() {
    return defaultProvider;
  }

  public void setDefaultProvider(String defaultProvider) {
    this.defaultProvider = defaultProvider;
  }

  public String getDefaultModel() {
    return defaultModel;
  }

  public void setDefaultModel(String defaultModel) {
    this.defaultModel = defaultModel;
  }

  public LlmGatewayOptions toOptions() {
    validate();
    return new LlmGatewayOptions(LlmProviderId.of(defaultProvider), LlmModelId.of(defaultModel));
  }

  public LlmModelSelector defaultSelector(String purpose) {
    return toOptions().defaultSelector(purpose);
  }

  private void validate() {
    if (defaultProvider == null || defaultProvider.isBlank()) {
      throw new IllegalArgumentException("LLM gateway default provider must not be blank");
    }
    if (defaultModel == null || defaultModel.isBlank()) {
      throw new IllegalArgumentException("LLM gateway default model must not be blank");
    }
  }
}
