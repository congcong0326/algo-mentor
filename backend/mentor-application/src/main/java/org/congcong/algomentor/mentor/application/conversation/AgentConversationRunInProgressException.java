package org.congcong.algomentor.mentor.application.conversation;

import java.util.Map;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;

public class AgentConversationRunInProgressException extends RuntimeException {

  private static final String MESSAGE = "当前会话正在生成回答";

  private final Map<String, Object> metadata;

  public AgentConversationRunInProgressException(long taskId) {
    super(MESSAGE);
    this.metadata = Map.of(AgentRuntimeMetadataKeys.TASK_ID, taskId);
  }

  public String code() {
    return AgentErrorCode.AGENT_RUN_IN_PROGRESS.name();
  }

  public Map<String, Object> metadata() {
    return metadata;
  }
}
