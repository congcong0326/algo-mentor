package org.congcong.algomentor.auth.model;

/**
 * 当前用户设置或修改邮箱登录密码的请求体。
 */
public record UserPasswordUpdateRequest(
    String currentPassword,
    String newPassword,
    String confirmPassword
) {
}
