package org.congcong.algomentor.auth.betaaccess.model;

/** 管理员概览使用的脱敏白名单汇总。 */
public record BetaAccessOverviewSummary(
    boolean emailAllowlistEnabled,
    long allowedEmailCount,
    long registeredAllowedEmailCount
) {
}
