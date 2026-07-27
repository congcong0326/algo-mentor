package org.congcong.algomentor.llm.core.provider;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 应用启动时冻结的 provider adapter 类型目录。 */
public final class LlmProviderAdapterRegistry {

  private final Map<LlmProviderType, LlmProviderAdapter> adapters;

  public LlmProviderAdapterRegistry(Collection<? extends LlmProviderAdapter> adapters) {
    Map<LlmProviderType, LlmProviderAdapter> values = new LinkedHashMap<>();
    if (adapters != null) {
      for (LlmProviderAdapter adapter : adapters) {
        if (adapter == null || adapter.providerType() == null) {
          throw new IllegalArgumentException("LLM provider adapter and provider type must not be null");
        }
        LlmProviderAdapter previous = values.putIfAbsent(adapter.providerType(), adapter);
        if (previous != null) {
          throw new IllegalStateException(
              "Duplicate LLM provider adapter type: " + adapter.providerType().value());
        }
      }
    }
    this.adapters = Map.copyOf(values);
  }

  public Optional<LlmProviderAdapter> find(LlmProviderType providerType) {
    return Optional.ofNullable(adapters.get(providerType));
  }

  public LlmProviderAdapter require(LlmProviderType providerType) {
    return find(providerType).orElseThrow(() -> new IllegalArgumentException(
        "LLM provider adapter type is not registered: " + providerType.value()));
  }

  public Map<LlmProviderType, LlmProviderAdapter> adapters() {
    return adapters;
  }
}
