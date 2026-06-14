package org.congcong.algomentor.llm.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record LlmCompletionResult(
    LlmMessage message,
    List<LlmToolCall> toolCalls,
    JsonNode structuredOutput,
    LlmFinishReason finishReason,
    LlmUsage usage,
    LlmProviderId provider,
    LlmModelId model,
    Map<String, Object> metadata
) {

  public LlmCompletionResult {
    if (message == null) {
      throw new IllegalArgumentException("LLM completion result message must not be null");
    }
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    finishReason = finishReason == null ? LlmFinishReason.UNKNOWN : finishReason;
    usage = usage == null ? LlmUsage.empty() : usage;
    if (provider == null) {
      throw new IllegalArgumentException("LLM completion result provider must not be null");
    }
    if (model == null) {
      throw new IllegalArgumentException("LLM completion result model must not be null");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
