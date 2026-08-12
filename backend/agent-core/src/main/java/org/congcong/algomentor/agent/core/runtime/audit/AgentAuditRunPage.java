package org.congcong.algomentor.agent.core.runtime.audit;

import java.util.List;

/** 分页 Agent run 审计结果。 */
public record AgentAuditRunPage(
    List<AgentAuditRunSummary> items,
    long total,
    int page,
    int pageSize,
    AgentAuditRunStatistics statistics
) {

  public AgentAuditRunPage(List<AgentAuditRunSummary> items, long total, int page, int pageSize) {
    this(items, total, page, pageSize, new AgentAuditRunStatistics(0, 0, 0, 0, null, null));
  }

  public AgentAuditRunPage {
    items = items == null ? List.of() : List.copyOf(items);
    if (total < 0 || page < 1 || pageSize < 1) {
      throw new IllegalArgumentException("Audit page values are invalid");
    }
    statistics = statistics == null ? new AgentAuditRunStatistics(0, 0, 0, 0, null, null) : statistics;
  }
}
