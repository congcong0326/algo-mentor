package org.congcong.algomentor.agent.core.runtime.repository;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;

public interface AgentConversationRepository {

  PreparedAgentRun createOrReuseRun(AgentRunPreparationRequest request);

  Optional<PreparedAgentRun> findRunByIdempotencyKey(String idempotencyKey);

  List<AgentMessage> recentMessages(long taskId, int messageLimit);
}
