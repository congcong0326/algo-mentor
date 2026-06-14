package org.congcong.algomentor.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

public interface AgentTool {

  LlmToolSpec spec();

  JsonNode execute(JsonNode arguments, AgentExecutionContext context);
}
