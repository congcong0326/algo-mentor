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

  /** 返回当前 turn 之前的最近消息，当前用户输入由调用方单独作为本轮消息追加。 */
  List<AgentMessage> recentMessagesBeforeTurn(long taskId, long turnId, int messageLimit);
}
