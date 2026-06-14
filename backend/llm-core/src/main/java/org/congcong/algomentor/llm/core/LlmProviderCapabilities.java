package org.congcong.algomentor.llm.core;

import java.util.Map;
import java.util.Set;

public record LlmProviderCapabilities(Set<LlmCapability> capabilities, Map<String, LlmModelDescriptor> models) {

  public LlmProviderCapabilities {
    capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    models = models == null ? Map.of() : Map.copyOf(models);
  }
}
