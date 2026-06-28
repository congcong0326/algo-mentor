package org.congcong.algomentor.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.model.PasswordCredential;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordUserServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-26T00:00:00Z");

  private final OAuth2LoginUserServiceTest.InMemoryAuthUserRepository repository =
      new OAuth2LoginUserServiceTest.InMemoryAuthUserRepository();
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  @Test
  void registerCreatesUserPasswordHashAndUserRole() {
    PasswordUserService service = new PasswordUserService(
        repository,
        passwordEncoder,
        Clock.fixed(NOW, ZoneOffset.UTC),
        null);

    AuthenticatedUserPrincipal principal = service.register("USER@example.com", "password-123", "User Name");

    assertThat(principal.userId()).isEqualTo(1L);
    assertThat(principal.email()).isEqualTo("USER@example.com");
    assertThat(principal.displayName()).isEqualTo("User Name");
    assertThat(principal.roles()).containsExactly(AuthRole.USER);
    assertThat(repository.users.get(1L).emailNormalized()).isEqualTo("user@example.com");
    PasswordCredential credential = repository.findPasswordCredentialByEmailNormalized("user@example.com").orElseThrow();
    assertThat(credential.passwordHash()).isNotEqualTo("password-123");
    assertThat(passwordEncoder.matches("password-123", credential.passwordHash())).isTrue();
  }

  @Test
  void configuredAdminEmailReceivesAdminRoleOnRegistration() {
    PasswordUserService service = new PasswordUserService(
        repository,
        passwordEncoder,
        Clock.fixed(NOW, ZoneOffset.UTC),
        new AdminEmailRoleService(repository, List.of("admin@example.com")));

    AuthenticatedUserPrincipal principal = service.register("admin@example.com", "password-123", "Admin User");

    assertThat(principal.roles()).containsExactly(AuthRole.USER, AuthRole.ADMIN);
  }

  @Test
  void duplicateEmailFailsRegistration() {
    PasswordUserService service = new PasswordUserService(
        repository,
        passwordEncoder,
        Clock.fixed(NOW, ZoneOffset.UTC),
        null);
    service.register("user@example.com", "password-123", "Same Name");

    assertThatThrownBy(() -> service.register("USER@example.com", "password-456", "Same Name"))
        .isInstanceOf(PasswordRegistrationException.class)
        .hasMessage("该邮箱已注册，请直接登录。");
  }

  @Test
  void duplicateDisplayNameIsAllowed() {
    PasswordUserService service = new PasswordUserService(
        repository,
        passwordEncoder,
        Clock.fixed(NOW, ZoneOffset.UTC),
        null);

    AuthenticatedUserPrincipal first = service.register("first@example.com", "password-123", "Same Name");
    AuthenticatedUserPrincipal second = service.register("second@example.com", "password-123", "Same Name");

    assertThat(first.displayName()).isEqualTo("Same Name");
    assertThat(second.displayName()).isEqualTo("Same Name");
  }

  @Test
  void missingDisplayNameFailsRegistration() {
    PasswordUserService service = new PasswordUserService(
        repository,
        passwordEncoder,
        Clock.fixed(NOW, ZoneOffset.UTC),
        null);

    assertThatThrownBy(() -> service.register("user@example.com", "password-123", " "))
        .isInstanceOf(PasswordRegistrationException.class)
        .hasMessage("请输入昵称。");
  }
}
