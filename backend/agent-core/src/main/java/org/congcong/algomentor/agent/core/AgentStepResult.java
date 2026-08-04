package org.congcong.algomentor.agent.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import org.congcong.algomentor.llm.core.provider.LlmProviderContinuation;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

public record AgentStepResult(
    List<LlmToolCall> toolCalls,
    LlmFinishReason finishReason,
    String content,
    @JsonIgnore LlmProviderContinuation providerContinuation
) {

  public AgentStepResult(List<LlmToolCall> toolCalls, LlmFinishReason finishReason) {
    this(toolCalls, finishReason, "", null);
  }

  public AgentStepResult(List<LlmToolCall> toolCalls, LlmFinishReason finishReason, String content) {
    this(toolCalls, finishReason, content, null);
  }

  public AgentStepResult {
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    finishReason = finishReason == null ? LlmFinishReason.UNKNOWN : finishReason;
    content = content == null ? "" : content;
  }

  public boolean requiresTools() {
    return finishReason == LlmFinishReason.TOOL_CALLS && !toolCalls.isEmpty();
  }
}
