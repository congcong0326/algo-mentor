package org.congcong.algomentor.ai.governance.repository.mybatis.model;

import org.congcong.algomentor.ai.governance.adminquery.AiEntryRequestMetrics;

public record AiEntryRequestMetricsRow(long total, long completed, long failed, long cancelled,
    long quotaRejected, long inProgress, long otherRejected) {
  public AiEntryRequestMetrics toDomain() {
    return new AiEntryRequestMetrics(total, completed, failed, cancelled, quotaRejected, inProgress, otherRejected);
  }
}
