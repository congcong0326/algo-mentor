package org.congcong.algomentor.llm.core;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class LlmModelSelector {
  private final LlmProviderId providerId;
  private final LlmModelId modelId;
  private final Set<LlmCapability> requiredCapabilities;
  private final String purpose;

  public LlmModelSelector(
      LlmProviderId providerId,
      LlmModelId modelId,
      Set<LlmCapability> requiredCapabilities,
      String purpose
  ) {
    this.providerId = providerId;
    this.modelId = modelId;
    this.requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
    this.purpose = purpose;
  }

  public static LlmModelSelector of(LlmProviderId providerId, LlmModelId modelId) {
    return new LlmModelSelector(providerId, modelId, Set.of(), null);
  }

  public static LlmModelSelector requiring(Set<LlmCapability> capabilities) {
    return new LlmModelSelector(null, null, capabilities, null);
  }

  public Optional<LlmProviderId> providerId() {
    return Optional.ofNullable(providerId);
  }

  public Optional<LlmModelId> modelId() {
    return Optional.ofNullable(modelId);
  }

  public Set<LlmCapability> requiredCapabilities() {
    return requiredCapabilities;
  }

  public String purpose() {
    return purpose;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LlmModelSelector that)) {
      return false;
    }
    return Objects.equals(providerId, that.providerId)
        && Objects.equals(modelId, that.modelId)
        && Objects.equals(requiredCapabilities, that.requiredCapabilities)
        && Objects.equals(purpose, that.purpose);
  }

  @Override
  public int hashCode() {
    return Objects.hash(providerId, modelId, requiredCapabilities, purpose);
  }

  @Override
  public String toString() {
    return "LlmModelSelector[providerId=%s, modelId=%s, requiredCapabilities=%s, purpose=%s]"
        .formatted(providerId, modelId, requiredCapabilities, purpose);
  }
}
