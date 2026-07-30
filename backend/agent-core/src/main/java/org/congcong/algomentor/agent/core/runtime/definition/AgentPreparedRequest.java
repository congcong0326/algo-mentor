package org.congcong.algomentor.agent.core.runtime.definition;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentExecutionOptions;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.llm.core.request.LlmMessage;

/** Definition 将受信业务输入转换后的不可变模型请求内容。 */
public record AgentPreparedRequest(
    List<LlmMessage> messages,
    Map<String, Object> metadata,
    AgentExecutionOptions executionOptions,
    PreparedAgentRun preparedRun,
    boolean idempotentReplay,
    AgentRunResource runResource,
    Long retryOfRunId
) {

  public AgentPreparedRequest(
      List<LlmMessage> messages,
      Map<String, Object> metadata,
      AgentExecutionOptions executionOptions
  ) {
    this(messages, metadata, executionOptions, null, false, AgentRunResource.none(), null);
  }

  public AgentPreparedRequest(
      List<LlmMessage> messages,
      Map<String, Object> metadata,
      AgentExecutionOptions executionOptions,
      PreparedAgentRun preparedRun,
      boolean idempotentReplay,
      AgentRunResource runResource
  ) {
    this(messages, metadata, executionOptions, preparedRun, idempotentReplay, runResource, null);
  }

  public AgentPreparedRequest {
    if (messages == null || messages.isEmpty()) {
      throw new IllegalArgumentException("Agent prepared request messages must not be empty");
    }
    messages = List.copyOf(messages);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    executionOptions = executionOptions == null ? AgentExecutionOptions.defaults() : executionOptions;
    if (idempotentReplay && preparedRun == null) {
      throw new IllegalArgumentException("Agent replay requires a prepared run");
    }
    if (retryOfRunId != null && retryOfRunId < 1) {
      throw new IllegalArgumentException("Agent retry source run id must be positive");
    }
    runResource = runResource == null ? AgentRunResource.none() : runResource;
  }
}
