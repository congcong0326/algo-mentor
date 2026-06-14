package org.congcong.algomentor.llm.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LlmRequestTest {

  @Test
  void createsSingleUserMessageRequest() {
    LlmRequest request = LlmRequest.userPrompt("gpt-test", "Explain binary search");

    assertThat(request.model()).isEqualTo("gpt-test");
    assertThat(request.messages())
        .containsExactly(LlmMessage.user("Explain binary search"));
  }

  @Test
  void rejectsRequestWithoutMessages() {
    assertThatThrownBy(() -> new LlmRequest("gpt-test", java.util.List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM request must include at least one message");
  }
}
