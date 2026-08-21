package org.congcong.algomentor.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessException;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessPolicy;
import org.congcong.algomentor.auth.betaaccess.service.BetaEmailAddress;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.loginsettings.service.AuthLoginSettingsProvider;
import org.congcong.algomentor.auth.model.OAuthAccount;
import org.congcong.algomentor.auth.model.OAuthProvider;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.transaction.annotation.Transactional;

public class OAuth2LoginUserService {

  private static final Logger log = LoggerFactory.getLogger(OAuth2LoginUserService.class);

  public static final String AUTH_USER_DISABLED_CODE = "auth_user_disabled";
  public static final String MISSING_SUBJECT_CODE = "missing_provider_subject";
  public static final String ACCOUNT_REGISTRATION_DISABLED_CODE = "account_registration_disabled";
  public static final String PROVIDER_LOGIN_DISABLED_CODE = "oauth_provider_login_disabled";

  private final AuthUserRepository authRepository;
  private final IdentityUserRepository identityRepository;
  private final Clock clock;
  private final AdminEmailRoleService adminEmailRoleService;
  private final BetaAccessPolicy betaAccessPolicy;
  private final AuthLoginSettingsProvider loginSettingsProvider;

  public OAuth2LoginUserService(
      AuthUserRepository authRepository,
      IdentityUserRepository identityRepository,
      Clock clock
  ) {
    this(authRepository, identityRepository, clock, null, null, new AuthProperties());
  }

  public OAuth2LoginUserService(
      AuthUserRepository authRepository,
      IdentityUserRepository identityRepository,
      Clock clock,
      AdminEmailRoleService adminEmailRoleService
  ) {
    this(authRepository, identityRepository, clock, adminEmailRoleService, null, new AuthProperties());
  }

  public OAuth2LoginUserService(
      AuthUserRepository authRepository,
      IdentityUserRepository identityRepository,
      Clock clock,
      AdminEmailRoleService adminEmailRoleService,
      BetaAccessPolicy betaAccessPolicy
  ) {
    this(
        authRepository,
        identityRepository,
        clock,
        adminEmailRoleService,
        betaAccessPolicy,
        new AuthProperties());
  }

  public OAuth2LoginUserService(
      AuthUserRepository authRepository,
      IdentityUserRepository identityRepository,
      Clock clock,
      AdminEmailRoleService adminEmailRoleService,
      BetaAccessPolicy betaAccessPolicy,
      AuthProperties authProperties
  ) {
    this(
        authRepository,
        identityRepository,
        clock,
        adminEmailRoleService,
        betaAccessPolicy,
        AuthLoginSettingsProvider.fromProperties(authProperties));
  }

  public OAuth2LoginUserService(
      AuthUserRepository authRepository,
      IdentityUserRepository identityRepository,
      Clock clock,
      AdminEmailRoleService adminEmailRoleService,
      BetaAccessPolicy betaAccessPolicy,
      AuthLoginSettingsProvider loginSettingsProvider
  ) {
    this.authRepository = authRepository;
    this.identityRepository = identityRepository;
    this.clock = clock;
    this.adminEmailRoleService = adminEmailRoleService;
    this.betaAccessPolicy = betaAccessPolicy;
    this.loginSettingsProvider = loginSettingsProvider;
  }

  @Transactional
  public AuthenticatedUserPrincipal syncGoogleUser(Map<String, Object> attributes) {
    return syncOAuthUser(OAuthProvider.GOOGLE, attributes);
  }

  @Transactional
  public AuthenticatedUserPrincipal syncOAuthUser(
      OAuthProvider provider,
      Map<String, Object> attributes
  ) {
    requireProviderLoginEnabled(provider);
    String subject = requiredAttribute(attributes, provider.subjectAttribute(), MISSING_SUBJECT_CODE);
    String email = stringAttribute(attributes, provider.emailAttribute());
    String emailNormalized = normalizeEmail(email);
    String displayName = firstNonBlank(
        stringAttribute(attributes, provider.displayNameAttribute()),
        stringAttribute(attributes, provider.fallbackDisplayNameAttribute()));
    String avatarUrl = stringAttribute(attributes, provider.avatarUrlAttribute());
    Instant now = Instant.now(clock);

    Optional<OAuthAccount> existingAccount = authRepository.findOAuthAccount(provider, subject);
    Optional<AuthUser> existingUserByEmail = existingAccount.isPresent()
        ? Optional.empty()
        : findUserByEmail(emailNormalized);
    requireAccountRegistrationAllowed(existingAccount, existingUserByEmail);
    requireBetaAccess(email, existingAccount);
    boolean createdAccount = existingAccount.isEmpty();
    OAuthAccount account = existingAccount
        .orElseGet(() -> createOAuthAccount(
            provider,
            subject,
            email,
            emailNormalized,
            displayName,
            avatarUrl,
            now,
            existingUserByEmail));

    AuthUser user = identityRepository.findUserById(account.userId())
        .orElseThrow(() -> authenticationException("auth_user_missing", "Authenticated user does not exist."));
    ensureActive(user);

    if (account.id() != null) {
      authRepository.updateOAuthAccountProfile(account.id(), email, displayName, avatarUrl, now);
    }
    AuthUser updatedUser = identityRepository.updateProfileAndLastLoginAt(
        user.id(),
        displayName,
        avatarUrl,
        now);
    ensureConfiguredAdminRole(updatedUser);
    List<AuthRole> roles = identityRepository.findRoles(updatedUser.id());
    List<AuthRole> effectiveRoles = roles.isEmpty() ? List.of(AuthRole.USER) : roles;
    log.info(
        "OAuth user synchronized. provider={} userId={} createdAccount={} emailPresent={} displayNamePresent={} avatarPresent={} roles={}",
        provider.value(),
        updatedUser.id(),
        createdAccount,
        email != null,
        displayName != null,
        avatarUrl != null,
        effectiveRoles);
    return toPrincipal(updatedUser, effectiveRoles);
  }

  private void requireBetaAccess(String providerEmail, Optional<OAuthAccount> existingAccount) {
    if (betaAccessPolicy == null) {
      return;
    }
    Optional<AuthUser> existingUser = existingAccount
        .flatMap(account -> identityRepository.findUserById(account.userId()));
    if (existingUser.isEmpty()) {
      String emailNormalized = BetaEmailAddress.normalize(providerEmail);
      if (!emailNormalized.isBlank()) {
        existingUser = identityRepository.findUserByEmailNormalized(emailNormalized);
      }
    }
    boolean hasAdminRole = existingUser
        .map(user -> identityRepository.findRoles(user.id()).contains(AuthRole.ADMIN))
        .orElse(false);
    try {
      betaAccessPolicy.requireAllowed(providerEmail, hasAdminRole);
    } catch (BetaAccessException exception) {
      throw authenticationException(exception.code().name(), exception.getMessage());
    }
  }

  private void ensureConfiguredAdminRole(AuthUser user) {
    if (adminEmailRoleService != null) {
      adminEmailRoleService.ensureAdminRole(user.id(), user.email());
    }
  }

  private OAuthAccount createOAuthAccount(
      OAuthProvider provider,
      String subject,
      String email,
      String emailNormalized,
      String displayName,
      String avatarUrl,
      Instant now,
      Optional<AuthUser> existingUserByEmail
  ) {
    AuthUser user = existingUserByEmail
        .orElseGet(() -> identityRepository.createUser(
            email,
            emailNormalized,
            displayName,
            avatarUrl,
            AuthUserStatus.ACTIVE,
            now));
    ensureActive(user);
    if (existingUserByEmail.isEmpty()) {
      identityRepository.addRole(user.id(), AuthRole.USER);
    }
    return authRepository.createOAuthAccount(new OAuthAccount(
        null,
        user.id(),
        provider,
        subject,
        email,
        displayName,
        avatarUrl,
        now,
        now));
  }

  private Optional<AuthUser> findUserByEmail(String emailNormalized) {
    return emailNormalized == null
        ? Optional.empty()
        : identityRepository.findUserByEmailNormalized(emailNormalized);
  }

  private void requireAccountRegistrationAllowed(
      Optional<OAuthAccount> existingAccount,
      Optional<AuthUser> existingUserByEmail
  ) {
    if (loginSettingsProvider.current().accountRegistrationEnabled()
        || existingAccount.isPresent()
        || existingUserByEmail.isPresent()) {
      return;
    }
    throw authenticationException(
        ACCOUNT_REGISTRATION_DISABLED_CODE,
        "New account registration is not currently available.");
  }

  private void requireProviderLoginEnabled(OAuthProvider provider) {
    if (loginSettingsProvider.oauthLoginEnabled(provider)) {
      return;
    }
    throw authenticationException(
        PROVIDER_LOGIN_DISABLED_CODE,
        "OAuth provider login is not currently available.");
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
    if (user.status() != AuthUserStatus.ACTIVE) {
      throw authenticationException(AUTH_USER_DISABLED_CODE, "User is disabled.");
    }
  }

  private static String requiredAttribute(Map<String, Object> attributes, String key, String errorCode) {
    String value = attributeAsString(attributes, key);
    if (value == null || value.isBlank()) {
      throw authenticationException(errorCode, "OAuth2 provider response is missing required subject.");
    }
    return value;
  }

  private static String stringAttribute(Map<String, Object> attributes, String key) {
    if (key == null) {
      return null;
    }
    Object value = attributes.get(key);
    return value instanceof String text && !text.isBlank() ? text : null;
  }

  private static String attributeAsString(Map<String, Object> attributes, String key) {
    Object value = attributes.get(key);
    if (value instanceof String text && !text.isBlank()) {
      return text;
    }
    return value instanceof Number number ? number.toString() : null;
  }

  private static String firstNonBlank(String first, String second) {
    return first == null || first.isBlank() ? second : first;
  }

  private static String normalizeEmail(String email) {
    String normalized = BetaEmailAddress.normalize(email);
    return normalized.isEmpty() ? null : normalized;
  }

  private static OAuth2AuthenticationException authenticationException(String code, String description) {
    return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
  }
}
