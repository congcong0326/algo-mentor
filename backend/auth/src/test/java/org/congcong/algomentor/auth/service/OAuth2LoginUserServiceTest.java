package org.congcong.algomentor.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.model.AuthUser;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.congcong.algomentor.auth.model.OAuthAccount;
import org.congcong.algomentor.auth.model.OAuthProvider;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

public class OAuth2LoginUserServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-22T07:00:00Z");
  private final InMemoryAuthUserRepository repository = new InMemoryAuthUserRepository();
  private final OAuth2LoginUserService service = new OAuth2LoginUserService(
      repository,
      Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void firstGoogleLoginCreatesUserRoleAndBinding() {
    AuthenticatedUserPrincipal principal = service.syncGoogleUser(googleAttributes(
        "google-sub-1",
        "USER@Example.COM",
        "User Name",
        "https://example.com/avatar.png"));

    assertThat(principal.userId()).isEqualTo(1L);
    assertThat(principal.email()).isEqualTo("USER@Example.COM");
    assertThat(principal.displayName()).isEqualTo("User Name");
    assertThat(principal.avatarUrl()).isEqualTo("https://example.com/avatar.png");
    assertThat(principal.roles()).containsExactly(AuthRole.USER);
    assertThat(principal.status()).isEqualTo(AuthUserStatus.ACTIVE);

    AuthUser createdUser = repository.users.get(1L);
    assertThat(createdUser.emailNormalized()).isEqualTo("user@example.com");
    assertThat(createdUser.lastLoginAt()).isEqualTo(NOW);
    assertThat(repository.rolesByUserId.get(1L)).containsExactly(AuthRole.USER);
    assertThat(repository.oauthAccountsByKey)
        .containsKey(OAuthProvider.GOOGLE.value() + ":google-sub-1");
  }

  @Test
  void repeatGoogleLoginUpdatesProviderFieldsAndLastLoginAt() {
    AuthUser user = repository.createUser(
        "old@example.com",
        "old@example.com",
        "Old Name",
        "old-avatar",
        AuthUserStatus.ACTIVE,
        Instant.parse("2026-06-21T00:00:00Z"));
    repository.addRole(user.id(), AuthRole.USER);
    repository.createOAuthAccount(new OAuthAccount(
        null,
        user.id(),
        OAuthProvider.GOOGLE,
        "google-sub-1",
        "old@example.com",
        "Old Name",
        "old-avatar",
        NOW.minusSeconds(3600),
        NOW.minusSeconds(3600)));

    AuthenticatedUserPrincipal principal = service.syncGoogleUser(googleAttributes(
        "google-sub-1",
        "new@example.com",
        "New Name",
        "new-avatar"));

    assertThat(principal.userId()).isEqualTo(user.id());
    assertThat(principal.roles()).containsExactly(AuthRole.USER);
    assertThat(repository.createUserCalls).isEqualTo(1);
    OAuthAccount updatedAccount = repository.oauthAccountsByKey.get(OAuthProvider.GOOGLE.value() + ":google-sub-1");
    assertThat(updatedAccount.emailAtProvider()).isEqualTo("new@example.com");
    assertThat(updatedAccount.displayNameAtProvider()).isEqualTo("New Name");
    assertThat(updatedAccount.avatarUrlAtProvider()).isEqualTo("new-avatar");
    assertThat(repository.users.get(user.id()).lastLoginAt()).isEqualTo(NOW);
  }

  @Test
  void firstGoogleLoginReusesExistingUserWithSameEmailWhenBindingIsMissing() {
    AuthUser user = repository.createUser(
        "0326congcong@gmail.com",
        "0326congcong@gmail.com",
        "Existing User",
        "old-avatar",
        AuthUserStatus.ACTIVE,
        NOW.minusSeconds(3600));
    repository.addRole(user.id(), AuthRole.USER);

    AuthenticatedUserPrincipal principal = service.syncGoogleUser(googleAttributes(
        "google-sub-recovered",
        "0326CONGCONG@gmail.com",
        "Google User",
        "google-avatar"));

    assertThat(principal.userId()).isEqualTo(user.id());
    assertThat(repository.createUserCalls).isEqualTo(1);
    assertThat(repository.oauthAccountsByKey)
        .containsKey(OAuthProvider.GOOGLE.value() + ":google-sub-recovered");
    OAuthAccount account = repository.oauthAccountsByKey.get(
        OAuthProvider.GOOGLE.value() + ":google-sub-recovered");
    assertThat(account.userId()).isEqualTo(user.id());
    assertThat(repository.users.get(user.id()).lastLoginAt()).isEqualTo(NOW);
  }

  @Test
  void disabledUserCannotLogin() {
    AuthUser user = repository.createUser(
        "disabled@example.com",
        "disabled@example.com",
        "Disabled User",
        null,
        AuthUserStatus.DISABLED,
        NOW.minusSeconds(3600));
    repository.addRole(user.id(), AuthRole.USER);
    repository.createOAuthAccount(new OAuthAccount(
        null,
        user.id(),
        OAuthProvider.GOOGLE,
        "disabled-sub",
        "disabled@example.com",
        "Disabled User",
        null,
        NOW.minusSeconds(3600),
        NOW.minusSeconds(3600)));

    assertThatThrownBy(() -> service.syncGoogleUser(googleAttributes(
        "disabled-sub",
        "disabled@example.com",
        "Disabled User",
        null)))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  private static Map<String, Object> googleAttributes(
      String subject,
      String email,
      String name,
      String picture
  ) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("sub", subject);
    attributes.put("email", email);
    attributes.put("name", name);
    attributes.put("picture", picture);
    return attributes;
  }

  public static final class InMemoryAuthUserRepository implements AuthUserRepository {

    private final Map<Long, AuthUser> users = new HashMap<>();
    private final Map<String, OAuthAccount> oauthAccountsByKey = new HashMap<>();
    private final Map<Long, List<AuthRole>> rolesByUserId = new HashMap<>();
    private long nextUserId = 1L;
    private long nextOAuthAccountId = 1L;
    private int createUserCalls;

    @Override
    public Optional<OAuthAccount> findOAuthAccount(OAuthProvider provider, String providerSubject) {
      return Optional.ofNullable(oauthAccountsByKey.get(accountKey(provider, providerSubject)));
    }

    @Override
    public Optional<AuthUser> findUserById(long userId) {
      return Optional.ofNullable(users.get(userId));
    }

    @Override
    public Optional<AuthUser> findUserByEmailNormalized(String emailNormalized) {
      return users.values()
          .stream()
          .filter(user -> emailNormalized.equals(user.emailNormalized()))
          .findFirst();
    }

    @Override
    public AuthUser createUser(
        String email,
        String emailNormalized,
        String displayName,
        String avatarUrl,
        AuthUserStatus status,
        Instant now
    ) {
      createUserCalls++;
      AuthUser user = new AuthUser(
          nextUserId++,
          email,
          emailNormalized,
          displayName,
          avatarUrl,
          status,
          now,
          now,
          now);
      users.put(user.id(), user);
      return user;
    }

    @Override
    public void addRole(long userId, AuthRole role) {
      List<AuthRole> roles = rolesByUserId.computeIfAbsent(userId, ignored -> new ArrayList<>());
      if (!roles.contains(role)) {
        roles.add(role);
      }
    }

    @Override
    public List<AuthRole> findRoles(long userId) {
      return List.copyOf(rolesByUserId.getOrDefault(userId, List.of()));
    }

    @Override
    public OAuthAccount createOAuthAccount(OAuthAccount account) {
      OAuthAccount created = new OAuthAccount(
          nextOAuthAccountId++,
          account.userId(),
          account.provider(),
          account.providerSubject(),
          account.emailAtProvider(),
          account.displayNameAtProvider(),
          account.avatarUrlAtProvider(),
          account.createdAt(),
          account.updatedAt());
      oauthAccountsByKey.put(accountKey(created.provider(), created.providerSubject()), created);
      return created;
    }

    @Override
    public void updateOAuthAccountProfile(
        long accountId,
        String emailAtProvider,
        String displayNameAtProvider,
        String avatarUrlAtProvider,
        Instant updatedAt
    ) {
      oauthAccountsByKey.replaceAll((key, account) -> account.id() == accountId
          ? new OAuthAccount(
              account.id(),
              account.userId(),
              account.provider(),
              account.providerSubject(),
              emailAtProvider,
              displayNameAtProvider,
              avatarUrlAtProvider,
              account.createdAt(),
              updatedAt)
          : account);
    }

    @Override
    public AuthUser updateLastLoginAt(long userId, Instant lastLoginAt) {
      AuthUser user = users.get(userId);
      AuthUser updated = new AuthUser(
          user.id(),
          user.email(),
          user.emailNormalized(),
          user.displayName(),
          user.avatarUrl(),
          user.status(),
          user.createdAt(),
          lastLoginAt,
          lastLoginAt);
      users.put(userId, updated);
      return updated;
    }

    private static String accountKey(OAuthProvider provider, String providerSubject) {
      return provider.value() + ":" + providerSubject;
    }
  }
}
