package org.congcong.algomentor.agent.persistence.postgres.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunErrorUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStartUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunSuccessUpdate;

@Mapper
public interface AgentRunMapper {

  int markRunStarted(RunStartUpdate update);

  long insertAssistantMessage(
      @Param("taskId") long taskId,
      @Param("turnId") long turnId,
      @Param("runId") long runId,
      @Param("content") String content,
      @Param("tokenEstimate") int tokenEstimate,
      @Param("metadata") Map<String, Object> metadata,
      @Param("createdAt") Instant createdAt,
      @Param("updatedAt") Instant updatedAt
  );

  int markRunSucceeded(RunSuccessUpdate update);

  int markTurnSucceeded(
      @Param("turnId") long turnId,
      @Param("assistantMessageId") Long assistantMessageId,
      @Param("runId") long runId,
      @Param("updatedAt") Instant updatedAt
  );

  int markRunFailed(RunErrorUpdate update);

  int markTurnFailed(
      @Param("turnId") long turnId,
      @Param("updatedAt") Instant updatedAt
  );
}
