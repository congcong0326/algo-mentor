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
    return build(model, initialMessages(request), List.of());
  }

  static List<LlmMessage> initialMessages(AgentRequest request) {
    String prompt = "Explain the learning topic for an algorithm student: " + request.topic().title();
    return List.of(LlmMessage.user(prompt));
  }

  static LlmCompletionRequest build(String model, List<LlmMessage> messages, List<LlmToolSpec> tools) {
    return LlmCompletionRequest.builder()
        .modelSelector(new LlmModelSelector(null, LlmModelId.of(model), Set.of(), "topic-explanation"))
        .messages(messages)
        .tools(tools)
        .toolChoice(tools == null || tools.isEmpty() ? LlmToolChoice.none() : LlmToolChoice.auto())
        .build();
  }
}
