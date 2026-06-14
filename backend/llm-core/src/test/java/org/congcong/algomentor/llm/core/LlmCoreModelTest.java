package org.congcong.algomentor.llm.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LlmCoreModelTest {

  @Test
  void createsProviderAndModelIdentifiers() {
    assertThat(LlmProviderId.of("openai").value()).isEqualTo("openai");
    assertThat(LlmModelId.of("gpt-5.2").value()).isEqualTo("gpt-5.2");
  }

  @Test
  void rejectsBlankIdentifiers() {
    assertThatThrownBy(() -> LlmProviderId.of(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM provider id must not be blank");
    assertThatThrownBy(() -> LlmModelId.of(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM model id must not be blank");
  }

  @Test
  void calculatesTotalTokensWhenNotProvided() {
    LlmUsage usage = new LlmUsage(12, 8, 3, 2, null);

    assertThat(usage.totalTokens()).isEqualTo(20);
    assertThat(usage.cachedTokens()).isEqualTo(3);
    assertThat(usage.reasoningTokens()).isEqualTo(2);
  }
}
