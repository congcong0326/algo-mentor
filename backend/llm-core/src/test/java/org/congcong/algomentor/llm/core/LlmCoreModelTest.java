package org.congcong.algomentor.llm.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
  void rejectsToolMessageWithoutToolCallId() {
    assertThatThrownBy(() -> new LlmMessage(
        LlmMessage.Role.TOOL,
        java.util.List.of(new LlmContentPart.Text("x")),
        null,
        null,
        java.util.Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM tool message must include tool call id");
  }

  @Test
  void rejectsNonToolMessageWithToolCallId() {
    assertThatThrownBy(() -> new LlmMessage(
        LlmMessage.Role.USER,
        java.util.List.of(new LlmContentPart.Text("x")),
        null,
        "call-1",
        java.util.Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM non-tool message must not include tool call id");
  }

  @Test
  void rejectsNonToolMessageWithBlankToolCallId() {
    assertThatThrownBy(() -> new LlmMessage(
        LlmMessage.Role.USER,
        java.util.List.of(new LlmContentPart.Text("x")),
        null,
        " ",
        java.util.Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM non-tool message must not include tool call id");
  }

  @Test
  void rejectsEmptyContentForNonAssistantMessages() {
    assertThatThrownBy(() -> new LlmMessage(
        LlmMessage.Role.USER,
        List.of(),
        null,
        null,
        Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM message content must not be empty");
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

  @Test
  void createsCompletionRequestWithJsonSchemaFormat() {
    com.fasterxml.jackson.databind.JsonNode schema =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("type", "object");

    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(LlmProviderId.of("openai"), LlmModelId.of("gpt-5.2")))
        .messages(java.util.List.of(LlmMessage.user("Create a plan")))
        .responseFormat(new LlmResponseFormat.JsonSchema("plan", schema, true))
        .build();

    assertThat(request.modelSelector().providerId()).contains(LlmProviderId.of("openai"));
    assertThat(request.responseFormat()).isInstanceOf(LlmResponseFormat.JsonSchema.class);
    assertThat(request.toolChoice().mode()).isEqualTo(LlmToolChoice.Mode.AUTO);
  }

  @Test
  void rejectsToolChoiceThatRequiresMissingTools() {
    assertThatThrownBy(() -> LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(LlmProviderId.of("openai"), LlmModelId.of("gpt-5.2")))
        .messages(List.of(LlmMessage.user("Create a plan")))
        .toolChoice(LlmToolChoice.required())
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM tool choice requires at least one tool");

    assertThatThrownBy(() -> LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(LlmProviderId.of("openai"), LlmModelId.of("gpt-5.2")))
        .messages(List.of(LlmMessage.user("Create a plan")))
        .toolChoice(LlmToolChoice.specific("search_problem"))
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM tool choice requires at least one tool");
  }

  @Test
  void rejectsSpecificToolChoiceThatDoesNotMatchDeclaredTool() {
    com.fasterxml.jackson.databind.JsonNode schema =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("type", "object");

    assertThatThrownBy(() -> LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(LlmProviderId.of("openai"), LlmModelId.of("gpt-5.2")))
        .messages(List.of(LlmMessage.user("Create a plan")))
        .tools(List.of(new LlmToolSpec("search_problem", "Search an algorithm problem", schema, true)))
        .toolChoice(LlmToolChoice.specific("explain_problem"))
        .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM specific tool choice must match a declared tool");
  }

  @Test
  void copiesCompletionRequestWithNewModelSelector() {
    LlmModelSelector originalSelector = LlmModelSelector.of(LlmProviderId.of("openai"), LlmModelId.of("gpt-5.2"));
    LlmModelSelector newSelector = LlmModelSelector.of(LlmProviderId.of("anthropic"), LlmModelId.of("claude-sonnet"));
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(originalSelector)
        .messages(List.of(LlmMessage.user("Create a plan")))
        .metadata(Map.of("requestId", "req-1"))
        .build();

    LlmCompletionRequest copy = request.withModelSelector(newSelector);

    assertThat(request.modelSelector().providerId()).contains(LlmProviderId.of("openai"));
    assertThat(request.modelSelector().modelId()).contains(LlmModelId.of("gpt-5.2"));
    assertThat(copy.modelSelector().providerId()).contains(LlmProviderId.of("anthropic"));
    assertThat(copy.modelSelector().modelId()).contains(LlmModelId.of("claude-sonnet"));
    assertThat(copy.messages()).isEqualTo(request.messages());
    assertThat(copy.options()).isEqualTo(request.options());
    assertThat(copy.tools()).isEqualTo(request.tools());
    assertThat(copy.toolChoice()).isEqualTo(request.toolChoice());
    assertThat(copy.responseFormat()).isEqualTo(request.responseFormat());
    assertThat(copy.metadata()).isEqualTo(request.metadata());
  }

  @Test
  void exposesOptionalModelSelectorAccessorsOnly() {
    LlmModelSelector selector = LlmModelSelector.of(LlmProviderId.of("openai"), LlmModelId.of("gpt-5.2"));

    assertThat(selector.providerId()).contains(LlmProviderId.of("openai"));
    assertThat(selector.modelId()).contains(LlmModelId.of("gpt-5.2"));
    assertThat(Arrays.stream(LlmModelSelector.class.getMethods()).map(Method::getName))
        .doesNotContain("provider", "model");
  }

  @Test
  void rejectsInvalidGenerationOptionTimeoutAndStopSequences() {
    assertThatThrownBy(() -> new LlmGenerationOptions(null, null, null, List.of(), null, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM timeout must be positive");

    assertThatThrownBy(() -> new LlmGenerationOptions(null, null, null, List.of("stop", " "), null, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM stop sequences must not be blank");

    assertThatThrownBy(() -> new LlmGenerationOptions(null, null, null, Arrays.asList("stop", null), null, Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LLM stop sequences must not be blank");
  }

  @Test
  void createsCompletionResultWithStructuredOutput() {
    com.fasterxml.jackson.databind.JsonNode output =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("title", "Binary Search");

    LlmCompletionResult result = new LlmCompletionResult(
        LlmMessage.assistant("done"),
        java.util.List.of(),
        output,
        LlmFinishReason.STOP,
        LlmUsage.empty(),
        LlmProviderId.of("openai"),
        LlmModelId.of("gpt-5.2"),
        java.util.Map.of("requestId", "req-1"));

    assertThat(result.structuredOutput()).isEqualTo(output);
    assertThat(result.metadata()).containsEntry("requestId", "req-1");
  }

  @Test
  void createsCompletionResultWithOnlyToolCalls() {
    LlmProviderId provider = LlmProviderId.of("openai");
    LlmModelId model = LlmModelId.of("gpt-5.2");
    LlmToolCall toolCall = new LlmToolCall(
        "call-1",
        "search_problem",
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("query", "binary search"));

    LlmCompletionResult result = new LlmCompletionResult(
        LlmMessage.assistant(),
        List.of(toolCall),
        null,
        LlmFinishReason.TOOL_CALLS,
        LlmUsage.empty(),
        provider,
        model,
        Map.of());

    assertThat(result.message().text()).isEmpty();
    assertThat(result.toolCalls()).containsExactly(toolCall);
  }
}
