package org.congcong.algomentor.auth.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.auth.model.AuthPermission;
import org.congcong.algomentor.auth.model.AuthRole;

/**
 * 根据本地角色推导前端展示与路由体验所需的权限能力。
 */
public class AuthPermissionService {

  public List<String> permissionsFor(List<AuthRole> roles) {
    Set<AuthPermission> permissions = new LinkedHashSet<>();
    if (roles != null && roles.contains(AuthRole.USER)) {
      permissions.add(AuthPermission.LEARNING_PLAN_READ_OWN);
      permissions.add(AuthPermission.LEARNING_PLAN_WRITE_OWN);
      permissions.add(AuthPermission.PRACTICE_SESSION_WRITE_OWN);
    }
    if (roles != null && roles.contains(AuthRole.ADMIN)) {
      permissions.add(AuthPermission.LEARNING_PLAN_READ_OWN);
      permissions.add(AuthPermission.LEARNING_PLAN_WRITE_OWN);
      permissions.add(AuthPermission.PRACTICE_SESSION_WRITE_OWN);
      permissions.add(AuthPermission.PROBLEM_WRITE);
      permissions.add(AuthPermission.USER_MANAGE);
      permissions.add(AuthPermission.DEBUG_ACCESS);
    }
    return permissions.stream()
        .map(AuthPermission::value)
        .toList();
  }
}
