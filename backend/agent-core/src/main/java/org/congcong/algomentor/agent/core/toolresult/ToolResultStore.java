package org.congcong.algomentor.agent.core.toolresult;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

public interface ToolResultStore {

  StoredToolResult saveToolResult(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      JsonNode redactedResult,
      String serializedResult,
      String contentType,
      String redactionPolicyVersion
  );

  Optional<StoredToolResult> findByResultRef(AgentLoopContext context, String resultRef);

  default Optional<StoredToolResult> findByResultRef(String resultRef) {
    return findByResultRef(null, resultRef);
  }
}
