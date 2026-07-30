package org.congcong.algomentor.auth.model;

/**
 * 未登录页面与已登录设置页共用的认证入口能力快照。
 */
public record AuthCapabilitiesResponse(
    boolean passwordLoginEnabled,
    boolean passwordRegistrationEnabled
) {
}
