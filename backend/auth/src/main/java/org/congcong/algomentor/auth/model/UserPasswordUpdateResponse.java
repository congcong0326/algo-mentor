package org.congcong.algomentor.auth.model;

import org.congcong.algomentor.auth.password.UserPasswordUpdateOperation;

/**
 * 当前用户密码写入成功后的低敏感响应体。
 */
public record UserPasswordUpdateResponse(
    boolean passwordConfigured,
    UserPasswordUpdateOperation operation,
    int revokedSessionCount
) {
}
