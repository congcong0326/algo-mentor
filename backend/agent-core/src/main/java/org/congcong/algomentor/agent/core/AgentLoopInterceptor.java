package org.congcong.algomentor.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

public interface AgentLoopInterceptor {

  default LlmCompletionRequest beforeLlmRequest(
      AgentLoopContext context,
      int stepIndex,
      LlmCompletionRequest request
  ) {
    return request;
  }

  default LlmToolCall beforeToolCall(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall
  ) {
    return toolCall;
  }

  default JsonNode afterToolCall(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      JsonNode result
  ) {
    return result;
  }
}
