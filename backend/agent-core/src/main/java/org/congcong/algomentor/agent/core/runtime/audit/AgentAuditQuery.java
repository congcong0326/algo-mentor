package org.congcong.algomentor.agent.core.runtime.audit;

import java.util.Optional;

/** Agent 运行态的只读审计查询端口。 */
public interface AgentAuditQuery {

  AgentAuditRunPage findRuns(AgentAuditRunFilter filter);

  Optional<AgentAuditRunDetail> findRun(long runId);

  /**
   * 查询 step 详情；最终 provider 请求 JSON 仅在 {@code includeRequestSnapshot} 为 true 时返回。
   */
  Optional<AgentAuditStepDetail> findStep(long runId, int stepIndex, boolean includeRequestSnapshot);

  default Optional<AgentAuditStepDetail> findStep(long runId, int stepIndex) {
    return findStep(runId, stepIndex, true);
  }

  Optional<AgentAuditToolResult> findToolResult(
      long runId,
      String toolCallId,
      boolean includeContent,
      int offset,
      int limit
  );
}
