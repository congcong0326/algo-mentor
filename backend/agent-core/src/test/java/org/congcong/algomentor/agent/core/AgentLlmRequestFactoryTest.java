package org.congcong.algomentor.agent.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.junit.jupiter.api.Test;

class AgentLlmRequestFactoryTest {

  @Test
  void mapsAgentExecutionOptionsToLlmCompletionRequest() {
    LlmGenerationOptions generationOptions = new LlmGenerationOptions(
        0.2,
        0.9,
        3000,
        List.of("STOP"),
        7L,
        Duration.ofSeconds(30));
    LlmResponseFormat.JsonSchema responseFormat = new LlmResponseFormat.JsonSchema(
        "learning_plan_draft",
        JsonNodeFactory.instance.objectNode().put("type", "object"),
        true);
    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("create plan")),
        Map.of("purpose", "learning-plan"),
        new AgentExecutionOptions(
            generationOptions,
            responseFormat,
            new AgentStructuredOutputOptions(
                StructuredOutputStrategy.PROVIDER_NATIVE,
                "learning_plan_draft",
                "v1",
                true)));

    LlmCompletionRequest llmRequest = AgentLlmRequestFactory.build(
        new LlmModelSelector(null, LlmModelId.of("gpt-test"), Set.of(), null),
        request);

    assertThat(llmRequest.options()).isEqualTo(generationOptions);
    assertThat(llmRequest.responseFormat()).isEqualTo(responseFormat);
    assertThat(llmRequest.metadata()).containsEntry("purpose", "learning-plan");
    assertThat(llmRequest.metadata())
        .containsEntry(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_STRATEGY, "PROVIDER_NATIVE")
        .containsEntry(AgentRuntimeMetadataKeys.SCHEMA_NAME, "learning_plan_draft")
        .containsEntry(AgentRuntimeMetadataKeys.SCHEMA_VERSION, "v1");
  }
}
