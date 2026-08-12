package org.congcong.algomentor.agent.persistence.postgres.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunFilter;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditRunDetailRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditRunRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditRunStatisticsRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditStepRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditToolCallRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditToolResultRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditTurnRow;

/** Agent 运行态审计的只读 MyBatis 查询。 */
@Mapper
public interface AgentAuditMapper {

  List<AgentAuditRunRow> findRuns(@Param("filter") AgentAuditRunFilter filter);

  long countRuns(@Param("filter") AgentAuditRunFilter filter);

  AgentAuditRunStatisticsRow findRunStatistics(@Param("filter") AgentAuditRunFilter filter);

  AgentAuditRunDetailRow findRun(@Param("runId") long runId);

  List<AgentAuditTurnRow> findTaskTurns(@Param("taskId") long taskId);

  List<AgentAuditStepRow> findSteps(@Param("runId") long runId);

  AgentAuditStepRow findStep(
      @Param("runId") long runId,
      @Param("stepIndex") int stepIndex,
      @Param("includeRequestSnapshot") boolean includeRequestSnapshot
  );

  List<AgentAuditToolCallRow> findToolCalls(
      @Param("runId") long runId,
      @Param("stepIndex") int stepIndex
  );

  AgentAuditToolResultRow findToolResult(
      @Param("runId") long runId,
      @Param("toolCallId") String toolCallId,
      @Param("includeContent") boolean includeContent,
      @Param("offset") int offset,
      @Param("limit") int limit
  );
}
