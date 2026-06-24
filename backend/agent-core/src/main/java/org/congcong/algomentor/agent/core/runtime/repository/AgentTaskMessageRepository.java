package org.congcong.algomentor.agent.core.runtime.repository;

import java.util.List;
import org.congcong.algomentor.agent.core.runtime.model.AgentAssistantSeedMessageRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskCreationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskRef;

public interface AgentTaskMessageRepository {

  AgentTaskRef createTask(AgentTaskCreationRequest request);

  AgentMessage createAssistantSeedMessage(AgentAssistantSeedMessageRequest request);

  List<AgentMessage> messages(long taskId, int messageLimit);
}
