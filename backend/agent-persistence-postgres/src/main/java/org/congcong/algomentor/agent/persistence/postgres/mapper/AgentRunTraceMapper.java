package org.congcong.algomentor.agent.persistence.postgres.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepEndUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepErrorUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepStartRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallEndUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallErrorUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallStorageUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallStartRow;

@Mapper
public interface AgentRunTraceMapper {

  int insertStepStart(RunStepStartRow row);

  int attachRequestSnapshot(
      @Param("runId") long runId,
      @Param("stepIndex") int stepIndex,
      @Param("requestSnapshotId") long requestSnapshotId
  );

  int markStepSucceeded(RunStepEndUpdate update);

  int markStepFailed(RunStepErrorUpdate update);

  int insertToolStart(ToolCallStartRow row);

  int markToolSucceeded(ToolCallEndUpdate update);

  int markToolFailed(ToolCallErrorUpdate update);

  Long findToolCallDbId(
      @Param("runId") long runId,
      @Param("stepIndex") int stepIndex,
      @Param("toolCallId") String toolCallId
  );

  int updateToolResultStorage(ToolCallStorageUpdate update);

  Long findRunIdByResultBlobId(@Param("blobId") long blobId);
}
