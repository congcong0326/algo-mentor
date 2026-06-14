package org.congcong.algomentor.llm.core;

import java.util.List;

/**
 * Compatibility API retained during migration to the provider/gateway completion contract.
 */
@Deprecated(forRemoval = false)
public record LlmRequest(String model, List<LlmMessage> messages) {

  public LlmRequest {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("LLM model must not be blank");
    }
    if (messages == null || messages.isEmpty()) {
      throw new IllegalArgumentException("LLM request must include at least one message");
    }
    messages = List.copyOf(messages);
  }

  public static LlmRequest userPrompt(String model, String prompt) {
    return new LlmRequest(model, List.of(LlmMessage.user(prompt)));
  }

  public LlmCompletionRequest toCompletionRequest(LlmProviderId providerId) {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(providerId, LlmModelId.of(model)))
        .messages(messages)
        .build();
  }
}
