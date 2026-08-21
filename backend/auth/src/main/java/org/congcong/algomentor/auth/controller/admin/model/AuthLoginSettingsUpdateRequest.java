package org.congcong.algomentor.auth.controller.admin.model;

/** 管理员一次性提交完整入口开关快照，防止部分更新制造无意组合。 */
public record AuthLoginSettingsUpdateRequest(
    Boolean accountRegistrationEnabled,
    Boolean passwordLoginEnabled,
    Boolean passwordRegistrationEnabled,
    Boolean googleLoginEnabled,
    Boolean githubLoginEnabled
) {
}
