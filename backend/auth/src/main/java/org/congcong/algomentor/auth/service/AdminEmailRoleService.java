package org.congcong.algomentor.auth.service;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;

/**
 * 通过配置的管理员邮箱白名单补齐本地管理员角色。
 */
public class AdminEmailRoleService {

  private final IdentityUserRepository identityRepository;
  private final Set<String> adminEmailsNormalized;

  public AdminEmailRoleService(IdentityUserRepository identityRepository, Collection<String> adminEmails) {
    this.identityRepository = identityRepository;
    this.adminEmailsNormalized = adminEmails == null
        ? Set.of()
        : adminEmails.stream()
            .map(AdminEmailRoleService::normalizeEmail)
            .filter(email -> !email.isBlank())
            .collect(Collectors.toUnmodifiableSet());
  }

  public void ensureAdminRole(long userId, String email) {
    if (isAdminEmail(email)) {
      identityRepository.addRole(userId, AuthRole.ADMIN);
    }
  }

  public boolean isAdminEmail(String email) {
    return adminEmailsNormalized.contains(normalizeEmail(email));
  }

  private static String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }
}
