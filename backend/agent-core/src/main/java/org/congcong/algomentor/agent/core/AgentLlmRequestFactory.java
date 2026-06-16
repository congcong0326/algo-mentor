package org.congcong.algomentor.agent.core;

import java.util.List;
import java.util.Set;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

final class AgentLlmRequestFactory {

  private AgentLlmRequestFactory() {
  }

  static LlmCompletionRequest build(String model, AgentRequest request) {
    return build(selectorFromModel(model), request);
  }

  static LlmCompletionRequest build(LlmModelSelector modelSelector, AgentRequest request) {
    return build(modelSelector, initialMessages(request), List.of());
  }

  static List<LlmMessage> initialMessages(AgentRequest request) {
    String prompt = "Explain the learning topic for an algorithm student: " + request.topic().title();
    return List.of(LlmMessage.user(prompt));
  }

  static LlmCompletionRequest build(String model, List<LlmMessage> messages, List<LlmToolSpec> tools) {
    return build(selectorFromModel(model), messages, tools);
  }

  static LlmCompletionRequest build(LlmModelSelector modelSelector, List<LlmMessage> messages, List<LlmToolSpec> tools) {
    return build(modelSelector, messages, tools, defaultToolChoice(tools));
  }

  static LlmCompletionRequest build(
      LlmModelSelector modelSelector,
      List<LlmMessage> messages,
      List<LlmToolSpec> tools,
      LlmToolChoice toolChoice
  ) {
    return LlmCompletionRequest.builder()
        .modelSelector(selectorForTopicExplanation(modelSelector))
        .messages(messages)
        .tools(tools)
        .toolChoice(tools == null || tools.isEmpty() ? LlmToolChoice.none() : toolChoice)
        .build();
  }

  static LlmModelSelector selectorFromModel(String model) {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("Agent LLM model must not be blank");
    }
    return new LlmModelSelector(null, LlmModelId.of(model), Set.of(), "topic-explanation");
  }

  private static LlmModelSelector selectorForTopicExplanation(LlmModelSelector selector) {
    if (selector == null) {
      throw new IllegalArgumentException("Agent LLM model selector must not be null");
    }
    return new LlmModelSelector(
        selector.providerId().orElse(null),
        selector.modelId().orElse(null),
        selector.requiredCapabilities(),
        "topic-explanation");
  }

  private static LlmToolChoice defaultToolChoice(List<LlmToolSpec> tools) {
    return tools == null || tools.isEmpty() ? LlmToolChoice.none() : LlmToolChoice.auto();
  }
}
