package org.congcong.algomentor.agent.core.runtime.repository;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.model.AgentAssistantSeedMessageRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentActiveRun;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskCreationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskRef;

public interface AgentTaskMessageRepository {

  AgentTaskRef createTask(AgentTaskCreationRequest request);

  AgentMessage createAssistantSeedMessage(AgentAssistantSeedMessageRequest request);

  List<AgentMessage> messages(long taskId, int messageLimit);

  Optional<AgentActiveRun> activeRun(long taskId);

  /** 当前 task 下指定 run 是否仍处于 running，供实时订阅鉴权与终态轮询使用。 */
  default boolean isActiveRun(long taskId, String runUuid) {
    return activeRun(taskId)
        .map(run -> run.runUuid().equals(runUuid))
        .orElse(false);
  }
}
