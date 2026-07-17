package org.congcong.algomentor.auth.betaaccess.repository.mybatis.model;

import org.congcong.algomentor.auth.betaaccess.model.BetaAccessOverviewSummary;

public record BetaAccessOverviewSummaryRow(boolean emailAllowlistEnabled, long allowedEmailCount,
    long registeredAllowedEmailCount) {
  public BetaAccessOverviewSummary toDomain() {
    return new BetaAccessOverviewSummary(emailAllowlistEnabled, allowedEmailCount, registeredAllowedEmailCount);
  }
}
