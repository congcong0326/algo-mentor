package org.congcong.algomentor.auth.session.admin.controller.model;

/**
 * 管理员会话列表请求参数。
 */
public record AdminAuthSessionListQuery(
    int page,
    int pageSize,
    String keyword,
    String activity
) {
}
