package org.congcong.algomentor.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

public class PasswordUserService {

  private static final int MIN_PASSWORD_LENGTH = 8;

  private final AuthUserRepository authRepository;
  private final IdentityUserRepository identityRepository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;
  private final AdminEmailRoleService adminEmailRoleService;

  public PasswordUserService(
      AuthUserRepository authRepository,
      IdentityUserRepository identityRepository,
      PasswordEncoder passwordEncoder,
      Clock clock,
      AdminEmailRoleService adminEmailRoleService
  ) {
    this.authRepository = authRepository;
    this.identityRepository = identityRepository;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
    this.adminEmailRoleService = adminEmailRoleService;
  }

  @Transactional
  public AuthenticatedUserPrincipal register(String email, String password, String displayName) {
    String normalizedEmail = normalizeEmail(email);
    String normalizedDisplayName = normalizeDisplayName(displayName);
    validateRegistration(normalizedEmail, password, normalizedDisplayName);
    if (identityRepository.findUserByEmailNormalized(normalizedEmail).isPresent()) {
      throw new PasswordRegistrationException(
          PasswordAuthErrorCode.AUTH_EMAIL_ALREADY_REGISTERED,
          "该邮箱已注册，请直接登录。");
    }

    Instant now = Instant.now(clock);
    AuthUser user = identityRepository.createUser(
        email.trim(),
        normalizedEmail,
        normalizedDisplayName,
        null,
        AuthUserStatus.ACTIVE,
        now);
    authRepository.createPasswordCredential(user.id(), passwordEncoder.encode(password), now);
    identityRepository.addRole(user.id(), AuthRole.USER);
    if (adminEmailRoleService != null) {
      adminEmailRoleService.ensureAdminRole(user.id(), user.email());
    }
    List<AuthRole> roles = identityRepository.findRoles(user.id());
    return toPrincipal(user, roles.isEmpty() ? List.of(AuthRole.USER) : roles);
  }

  private static void validateRegistration(String emailNormalized, String password, String displayName) {
    if (emailNormalized.isBlank() || !emailNormalized.contains("@")) {
      throw new PasswordRegistrationException(
          PasswordAuthErrorCode.AUTH_REQUEST_INVALID,
          "请输入有效邮箱。");
    }
    if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
      throw new PasswordRegistrationException(
          PasswordAuthErrorCode.AUTH_REQUEST_INVALID,
          "密码至少需要 8 个字符。");
    }
    if (displayName.isBlank()) {
      throw new PasswordRegistrationException(
          PasswordAuthErrorCode.AUTH_DISPLAY_NAME_REQUIRED,
          "请输入昵称。");
    }
  }

  private static AuthenticatedUserPrincipal toPrincipal(AuthUser user, List<AuthRole> roles) {
    return new AuthenticatedUserPrincipal(
        user.id(),
        user.email(),
        user.displayName(),
        user.avatarUrl(),
        roles,
        user.status());
  }

  private static String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  private static String normalizeDisplayName(String displayName) {
    return displayName == null ? "" : displayName.trim();
  }
}
