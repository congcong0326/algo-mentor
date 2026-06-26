package org.congcong.algomentor.auth.service;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.repository.AuthUserRepository;

/**
 * 通过配置的管理员邮箱白名单补齐本地管理员角色。
 */
public class AdminEmailRoleService {

  private final AuthUserRepository repository;
  private final Set<String> adminEmailsNormalized;

  public AdminEmailRoleService(AuthUserRepository repository, Collection<String> adminEmails) {
    this.repository = repository;
    this.adminEmailsNormalized = adminEmails == null
        ? Set.of()
        : adminEmails.stream()
            .map(AdminEmailRoleService::normalizeEmail)
            .filter(email -> !email.isBlank())
            .collect(Collectors.toUnmodifiableSet());
  }

  public void ensureAdminRole(long userId, String email) {
    if (isAdminEmail(email)) {
      repository.addRole(userId, AuthRole.ADMIN);
    }
  }

  public boolean isAdminEmail(String email) {
    return adminEmailsNormalized.contains(normalizeEmail(email));
  }

  private static String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }
}
