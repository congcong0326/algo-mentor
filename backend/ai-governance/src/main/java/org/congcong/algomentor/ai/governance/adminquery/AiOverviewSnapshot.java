package org.congcong.algomentor.ai.governance.adminquery;

import java.time.LocalDate;
import java.util.List;

/** 管理员概览的 AI 数据快照，成本保持 decimal 直到 API 映射。 */
public record AiOverviewSnapshot(
    LocalDate quotaDate,
    String quotaZone,
    AiEntryRequestMetrics entryRequests,
    AiUsageMetrics usage,
    List<AiQuotaRiskUser> quotaRisks
) {
  public AiOverviewSnapshot { quotaRisks = quotaRisks == null ? List.of() : List.copyOf(quotaRisks); }
}
