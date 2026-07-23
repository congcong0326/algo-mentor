package org.congcong.algomentor.auth.session.admin.model;

/**
 * 单会话撤销的幂等结果。
 */
public record AuthSessionAdminRevocation(
    String sessionRef,
    Long userId,
    boolean revoked,
    boolean alreadyOffline
) {
}
