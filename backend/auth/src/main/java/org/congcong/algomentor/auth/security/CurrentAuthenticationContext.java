package org.congcong.algomentor.auth.security;

/**
 * 由 Spring Security 上下文解析出的当前用户和认证方式。
 */
public record CurrentAuthenticationContext(
    AuthenticatedUserPrincipal principal,
    AuthSessionAuthenticationMethod method
) {
}
