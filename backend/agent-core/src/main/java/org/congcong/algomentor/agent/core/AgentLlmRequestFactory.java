package org.congcong.algomentor.agent.core;

import java.util.List;
import java.util.Set;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;

final class AgentLlmRequestFactory {

  private AgentLlmRequestFactory() {
  }

  static LlmCompletionRequest build(String model, AgentRequest request) {
    String prompt = "Explain the learning topic for an algorithm student: " + request.topic().title();
    return LlmCompletionRequest.builder()
        .modelSelector(new LlmModelSelector(null, LlmModelId.of(model), Set.of(), "topic-explanation"))
        .messages(List.of(LlmMessage.user(prompt)))
        .build();
  }
}
