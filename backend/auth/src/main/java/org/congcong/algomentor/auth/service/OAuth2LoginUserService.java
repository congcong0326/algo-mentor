package org.congcong.algomentor.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.model.AuthUser;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.congcong.algomentor.auth.model.OAuthAccount;
import org.congcong.algomentor.auth.model.OAuthProvider;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.transaction.annotation.Transactional;

public class OAuth2LoginUserService {

  private static final Logger log = LoggerFactory.getLogger(OAuth2LoginUserService.class);

  public static final String AUTH_USER_DISABLED_CODE = "auth_user_disabled";
  public static final String MISSING_SUBJECT_CODE = "missing_provider_subject";

  private final AuthUserRepository repository;
  private final Clock clock;

  public OAuth2LoginUserService(AuthUserRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public AuthenticatedUserPrincipal syncGoogleUser(Map<String, Object> attributes) {
    String subject = requiredString(attributes, "sub", MISSING_SUBJECT_CODE);
    String email = stringAttribute(attributes, "email");
    String emailNormalized = normalizeEmail(email);
    String displayName = stringAttribute(attributes, "name");
    String avatarUrl = stringAttribute(attributes, "picture");
    Instant now = Instant.now(clock);

    Optional<OAuthAccount> existingAccount = repository.findOAuthAccount(OAuthProvider.GOOGLE, subject);
    boolean createdAccount = existingAccount.isEmpty();
    OAuthAccount account = existingAccount
        .orElseGet(() -> createGoogleAccount(subject, email, emailNormalized, displayName, avatarUrl, now));

    AuthUser user = repository.findUserById(account.userId())
        .orElseThrow(() -> authenticationException("auth_user_missing", "Authenticated user does not exist."));
    ensureActive(user);

    if (account.id() != null) {
      repository.updateOAuthAccountProfile(account.id(), email, displayName, avatarUrl, now);
    }
    AuthUser updatedUser = repository.updateLastLoginAt(user.id(), now);
    List<AuthRole> roles = repository.findRoles(updatedUser.id());
    List<AuthRole> effectiveRoles = roles.isEmpty() ? List.of(AuthRole.USER) : roles;
    log.info(
        "Google OAuth user synchronized. userId={} createdAccount={} emailPresent={} displayNamePresent={} avatarPresent={} roles={}",
        updatedUser.id(),
        createdAccount,
        email != null,
        displayName != null,
        avatarUrl != null,
        effectiveRoles);
    return toPrincipal(updatedUser, effectiveRoles);
  }

  private OAuthAccount createGoogleAccount(
      String subject,
      String email,
      String emailNormalized,
      String displayName,
      String avatarUrl,
      Instant now
  ) {
    Optional<AuthUser> userByEmail = repository.findUserByEmailNormalized(emailNormalized);
    AuthUser user = userByEmail
        .orElseGet(() -> repository.createUser(
            email,
            emailNormalized,
            displayName,
            avatarUrl,
            AuthUserStatus.ACTIVE,
            now));
    if (userByEmail.isEmpty()) {
      repository.addRole(user.id(), AuthRole.USER);
    }
    return repository.createOAuthAccount(new OAuthAccount(
        null,
        user.id(),
        OAuthProvider.GOOGLE,
        subject,
        email,
        displayName,
        avatarUrl,
        now,
        now));
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

  private static void ensureActive(AuthUser user) {
    if (user.status() == AuthUserStatus.DISABLED) {
      throw authenticationException(AUTH_USER_DISABLED_CODE, "User is disabled.");
    }
  }

  private static String requiredString(Map<String, Object> attributes, String key, String errorCode) {
    String value = stringAttribute(attributes, key);
    if (value == null || value.isBlank()) {
      throw authenticationException(errorCode, "OAuth2 provider response is missing required subject.");
    }
    return value;
  }

  private static String stringAttribute(Map<String, Object> attributes, String key) {
    Object value = attributes.get(key);
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private static String normalizeEmail(String email) {
    return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
  }

  private static OAuth2AuthenticationException authenticationException(String code, String description) {
    return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
  }
}
