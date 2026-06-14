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

  @Test
  void createsTextAndToolResultMessages() {
    LlmMessage user = LlmMessage.user("Explain binary search");
    LlmMessage tool = LlmMessage.toolResult("call-1", com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("ok", true));

    assertThat(user.role()).isEqualTo(LlmMessage.Role.USER);
    assertThat(user.text()).isEqualTo("Explain binary search");
    assertThat(tool.role()).isEqualTo(LlmMessage.Role.TOOL);
    assertThat(tool.toolCallId()).isEqualTo("call-1");
  }

  @Test
  void createsToolSpecAndSpecificToolChoice() {
    com.fasterxml.jackson.databind.JsonNode schema =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("type", "object");

    LlmToolSpec spec = new LlmToolSpec("search_problem", "Search an algorithm problem", schema, true);
    LlmToolChoice choice = LlmToolChoice.specific("search_problem");

    assertThat(spec.name()).isEqualTo("search_problem");
    assertThat(spec.strict()).isTrue();
    assertThat(choice.mode()).isEqualTo(LlmToolChoice.Mode.SPECIFIC);
    assertThat(choice.toolName()).isEqualTo("search_problem");
  }
}
