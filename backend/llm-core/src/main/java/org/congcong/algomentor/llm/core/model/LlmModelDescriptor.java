package org.congcong.algomentor.llm.core.model;

import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;

/**
 * 描述可用的 LLM 模型及其提供商、限制、默认设置和支持的功能。
 */
public record LlmModelDescriptor(
    LlmProviderId providerId,
    LlmModelId modelId,
    String displayName,
    Set<LlmCapability> supportedCapabilities,
    int contextWindowTokens,
    int maxOutputTokens,
    LlmGenerationOptions defaultGenerationOptions,
    Map<String, Object> metadata
) {

  public LlmModelDescriptor {
    if (providerId == null) {
      throw new IllegalArgumentException("LLM model descriptor provider id must not be null");
    }
    if (modelId == null) {
      throw new IllegalArgumentException("LLM model descriptor model id must not be null");
    }
    displayName = displayName == null || displayName.isBlank() ? modelId.value() : displayName;
    supportedCapabilities = supportedCapabilities == null ? Set.of() : Set.copyOf(supportedCapabilities);
    if (contextWindowTokens < 0 || maxOutputTokens < 0) {
      throw new IllegalArgumentException("LLM model token limits must not be negative");
    }
    defaultGenerationOptions = defaultGenerationOptions == null ? LlmGenerationOptions.defaults() : defaultGenerationOptions;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
