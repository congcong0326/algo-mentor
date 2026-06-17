package org.congcong.algomentor.agent.core;

import java.util.List;
import java.util.Map;
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
    return build(modelSelector, initialMessages(request), List.of(), null, request.metadata());
  }

  static List<LlmMessage> initialMessages(AgentRequest request) {
    return request.messages();
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
    return build(modelSelector, messages, tools, toolChoice, Map.of());
  }

  static LlmCompletionRequest build(
      LlmModelSelector modelSelector,
      List<LlmMessage> messages,
      List<LlmToolSpec> tools,
      LlmToolChoice toolChoice,
      Map<String, Object> metadata
  ) {
    return LlmCompletionRequest.builder()
        .modelSelector(validatedSelector(modelSelector))
        .messages(messages)
        .tools(tools)
        .toolChoice(tools == null || tools.isEmpty() ? LlmToolChoice.none() : toolChoice)
        .metadata(metadata)
        .build();
  }

  static LlmModelSelector selectorFromModel(String model) {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("Agent LLM model must not be blank");
    }
    return new LlmModelSelector(null, LlmModelId.of(model), Set.of(), null);
  }

  private static LlmModelSelector validatedSelector(LlmModelSelector selector) {
    if (selector == null) {
      throw new IllegalArgumentException("Agent LLM model selector must not be null");
    }
    return selector;
  }

  private static LlmToolChoice defaultToolChoice(List<LlmToolSpec> tools) {
    return tools == null || tools.isEmpty() ? LlmToolChoice.none() : LlmToolChoice.auto();
  }
}
