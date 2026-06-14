package org.congcong.algomentor.llm.core;

import java.util.Optional;
import java.util.Set;

public record LlmModelSelector(
    LlmProviderId provider,
    LlmModelId model,
    Set<LlmCapability> requiredCapabilities,
    String purpose
) {

  public LlmModelSelector {
    requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
  }

  public static LlmModelSelector of(LlmProviderId providerId, LlmModelId modelId) {
    return new LlmModelSelector(providerId, modelId, Set.of(), null);
  }

  public static LlmModelSelector requiring(Set<LlmCapability> capabilities) {
    return new LlmModelSelector(null, null, capabilities, null);
  }

  public Optional<LlmProviderId> providerId() {
    return Optional.ofNullable(provider);
  }

  public Optional<LlmModelId> modelId() {
    return Optional.ofNullable(model);
  }
}
