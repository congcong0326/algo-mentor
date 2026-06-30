package org.congcong.algomentor.auth.model;

/**
 * 后端按角色推导给前端使用的权限能力标识。
 */
public enum AuthPermission {

  LEARNING_PLAN_READ_OWN("learning-plan:read:own"),
  LEARNING_PLAN_WRITE_OWN("learning-plan:write:own"),
  PRACTICE_SESSION_WRITE_OWN("practice-session:write:own"),
  PROBLEM_READ("problem:read"),
  PROBLEM_WRITE("problem:write"),
  USER_MANAGE("user:manage"),
  DEBUG_ACCESS("debug:access");

  private final String value;

  AuthPermission(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
