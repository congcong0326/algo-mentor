package org.congcong.algomentor.auth.betaaccess.model;

/** 指定用户是否存在白名单记录，不回显邮箱内容。 */
public record BetaAccessUserMembership(long userId, boolean allowed, Long allowedEmailId) {
}
