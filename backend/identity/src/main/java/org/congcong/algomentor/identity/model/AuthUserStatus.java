package org.congcong.algomentor.identity.model;

/**
 * 本地身份用户状态；认证只允许 ACTIVE 登录。
 */
public enum AuthUserStatus {
  ACTIVE,
  DISABLED,
  DELETED
}
