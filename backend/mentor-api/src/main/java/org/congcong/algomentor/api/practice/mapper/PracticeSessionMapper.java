package org.congcong.algomentor.api.practice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.practice.mapper.model.PracticeProgressRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeSessionRow;

@Mapper
public interface PracticeSessionMapper {

  PracticeProgressRow upsertProgress(
      @Param("userId") long userId,
      @Param("planId") long planId,
      @Param("phaseIndex") int phaseIndex,
      @Param("problemSlug") String problemSlug
  );

  PracticeSessionRow upsertSession(
      @Param("userId") long userId,
      @Param("planId") long planId,
      @Param("phaseIndex") int phaseIndex,
      @Param("problemSlug") String problemSlug,
      @Param("locale") String locale
  );

  PracticeSessionRow findSessionByIdForUser(@Param("sessionId") long sessionId, @Param("userId") long userId);

  PracticeSessionRow attachAgentTask(@Param("sessionId") long sessionId, @Param("agentTaskId") long agentTaskId);

  PracticeSessionRow attachProblemStatementMessage(
      @Param("sessionId") long sessionId,
      @Param("messageId") long messageId
  );

  PracticeProgressRow updateProgressStatus(
      @Param("sessionId") long sessionId,
      @Param("userId") long userId,
      @Param("status") String status
  );

  int touchLastMessageAt(@Param("sessionId") long sessionId);
}
