package org.congcong.algomentor.auth.session.admin.model;

/**
 * 不受列表筛选影响的有效认证会话汇总。
 */
public record AuthSessionAdminSummary(
    long validSessionCount,
    long activeSessionCount,
    long validUserCount
) {
}
