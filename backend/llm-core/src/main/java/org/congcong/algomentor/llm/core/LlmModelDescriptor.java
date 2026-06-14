package org.congcong.algomentor.llm.core;

import java.util.Map;
import java.util.Set;

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
