package org.congcong.algomentor.ai.governance.adminquery;

import java.time.LocalDate;

/** 管理员用量面板顶部的全局汇总。 */
public record AiUsageSummary(
    LocalDate from,
    LocalDate to,
    String quotaZone,
    long admittedEntryRequestCount,
    AiUsageMetrics metrics
) {
}
