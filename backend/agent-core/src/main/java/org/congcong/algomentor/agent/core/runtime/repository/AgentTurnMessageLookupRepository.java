package org.congcong.algomentor.agent.core.runtime.repository;

import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;

public interface AgentTurnMessageLookupRepository {

  Optional<AgentTurnMessages> findByRunId(long runId);
}
