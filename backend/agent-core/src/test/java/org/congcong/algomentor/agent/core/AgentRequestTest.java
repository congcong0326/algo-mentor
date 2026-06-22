package org.congcong.algomentor.agent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.junit.jupiter.api.Test;

class AgentRequestTest {

  @Test
  void defaultsExecutionOptionsForLegacyConstructor() {
    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("question")),
        Map.of("title", "Question"));

    assertThat(request.executionOptions().generationOptions()).isNotNull();
    assertThat(request.executionOptions().responseFormat()).isInstanceOf(LlmResponseFormat.Text.class);
    assertThat(request.executionOptions().structuredOutput().strategy()).isEqualTo(StructuredOutputStrategy.NONE);
  }

  @Test
  void keepsExplicitExecutionOptionsImmutable() {
    AgentExecutionOptions options = new AgentExecutionOptions(
        null,
        new LlmResponseFormat.JsonObject(),
        new AgentStructuredOutputOptions(
            StructuredOutputStrategy.PROVIDER_NATIVE,
            "learning_plan_draft",
            "v1",
            true));

    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("question")),
        Map.of(),
        options);

    assertThat(request.executionOptions().responseFormat()).isInstanceOf(LlmResponseFormat.JsonObject.class);
    assertThat(request.executionOptions().structuredOutput().schemaName()).isEqualTo("learning_plan_draft");
    assertThat(request.executionOptions().structuredOutput().schemaVersion()).isEqualTo("v1");
    assertThat(request.executionOptions().structuredOutput().required()).isTrue();
  }
}
