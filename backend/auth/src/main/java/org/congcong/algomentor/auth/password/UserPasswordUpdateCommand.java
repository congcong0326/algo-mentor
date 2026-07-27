package org.congcong.algomentor.auth.password;

/**
 * 设置页提交的密码更新内容及当前 HTTP Session 标识。
 */
public record UserPasswordUpdateCommand(
    String currentPassword,
    String newPassword,
    String confirmPassword,
    String currentSessionId
) {
}
