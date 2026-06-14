package org.congcong.algomentor.llm.core.response;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

/**
 * 已完成的 LLM 调用所产生的不可变结果，包含消息、工具调用、输出、用量及来源信息。
 */
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
