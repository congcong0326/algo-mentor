package org.congcong.algomentor.llm.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapter;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapterRegistry;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderInstanceSpec;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.junit.jupiter.api.Test;

class LlmProviderAdapterRegistryTest {

  @Test
  void registersOneAdapterPerNormalizedProviderType() {
    StubAdapter adapter = new StubAdapter("OPENAI");

    LlmProviderAdapterRegistry registry = new LlmProviderAdapterRegistry(List.of(adapter));

    assertThat(registry.find(LlmProviderType.of("openai"))).containsSame(adapter);
  }

  @Test
  void rejectsDuplicateProviderTypesAtStartup() {
    assertThatThrownBy(() -> new LlmProviderAdapterRegistry(List.of(
        new StubAdapter("openai"), new StubAdapter("OPENAI"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate LLM provider adapter type");
  }

  private record StubAdapter(LlmProviderType providerType) implements LlmProviderAdapter {

    private StubAdapter(String providerType) {
      this(LlmProviderType.of(providerType));
    }

    @Override
    public String displayName() {
      return providerType.value();
    }

    @Override
    public Set<LlmCapability> supportedCapabilities() {
      return Set.of(LlmCapability.CHAT_COMPLETION);
    }

    @Override
    public void validateConfig(JsonNode config) {
    }

    @Override
    public LlmProviderClient createClient(LlmProviderInstanceSpec instance) {
      throw new UnsupportedOperationException();
    }
  }
}
