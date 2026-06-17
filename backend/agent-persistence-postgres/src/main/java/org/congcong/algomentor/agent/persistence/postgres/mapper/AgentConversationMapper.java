package org.congcong.algomentor.agent.persistence.postgres.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentRunRecord;

@Mapper
public interface AgentConversationMapper {

  int lockIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

  Long findRunIdByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

  AgentRunRecord findRunRecord(@Param("runId") long runId);

  long insertTask(
      @Param("userId") Long userId,
      @Param("title") String title,
      @Param("systemPrompt") String systemPrompt
  );

  long insertTurn(@Param("taskId") long taskId);

  long insertUserMessage(
      @Param("taskId") long taskId,
      @Param("turnId") long turnId,
      @Param("content") String content,
      @Param("tokenEstimate") int tokenEstimate
  );

  long insertRun(
      @Param("taskId") long taskId,
      @Param("turnId") long turnId,
      @Param("runUuid") String runUuid,
      @Param("idempotencyKey") String idempotencyKey,
      @Param("maxSteps") int maxSteps
  );

  int attachTurnUserMessageAndRun(
      @Param("turnId") long turnId,
      @Param("userMessageId") long userMessageId,
      @Param("runId") long runId
  );

  List<AgentMessage> recentMessages(
      @Param("taskId") long taskId,
      @Param("messageLimit") int messageLimit
  );
}
