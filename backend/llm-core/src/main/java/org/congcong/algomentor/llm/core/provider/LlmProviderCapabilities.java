package org.congcong.algomentor.llm.core.provider;

import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.llm.core.model.LlmModelDescriptor;

/**
 * 汇总提供商级别的能力以及该提供商公开的模型。
 */
public record LlmProviderCapabilities(Set<LlmCapability> capabilities, Map<String, LlmModelDescriptor> models) {

  public LlmProviderCapabilities {
    capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    models = models == null ? Map.of() : Map.copyOf(models);
  }
}
