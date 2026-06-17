package org.congcong.algomentor.agent.core.runtime.repository;

import java.util.List;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;

public interface AgentConversationRepository {

  PreparedAgentRun createOrReuseRun(AgentRunPreparationRequest request);

  List<AgentMessage> recentMessages(long taskId, int messageLimit);
}
