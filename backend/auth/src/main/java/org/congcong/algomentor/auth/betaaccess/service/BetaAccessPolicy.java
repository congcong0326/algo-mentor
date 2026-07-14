package org.congcong.algomentor.auth.betaaccess.service;

import org.congcong.algomentor.auth.betaaccess.model.BetaAccessDecision;
import org.congcong.algomentor.auth.betaaccess.repository.BetaAccessRepository;
import org.congcong.algomentor.auth.service.AdminEmailRoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetaAccessPolicy {

  private static final Logger log = LoggerFactory.getLogger(BetaAccessPolicy.class);

  private final BetaAccessRepository repository;
  private final AdminEmailRoleService adminEmailRoleService;

  public BetaAccessPolicy(
      BetaAccessRepository repository,
      AdminEmailRoleService adminEmailRoleService
  ) {
    this.repository = repository;
    this.adminEmailRoleService = adminEmailRoleService;
  }

  public BetaAccessDecision evaluate(String email, boolean hasAdminRole) {
    boolean enabled = repository.findSettings()
        .map(settings -> settings.emailAllowlistEnabled())
        .orElseGet(() -> {
          log.error("Beta access settings row is missing; treating allowlist as disabled.");
          return false;
        });
    if (!enabled) {
      return BetaAccessDecision.ALLOWED_ALLOWLIST_DISABLED;
    }
    if (hasAdminRole || (adminEmailRoleService != null && adminEmailRoleService.isAdminEmail(email))) {
      return BetaAccessDecision.ALLOWED_TRUSTED_ADMIN;
    }
    if (!BetaEmailAddress.isValid(email)) {
      return BetaAccessDecision.DENIED_INVALID_EMAIL;
    }
    return repository.isAllowedEmail(BetaEmailAddress.normalize(email))
        ? BetaAccessDecision.ALLOWED_EMAIL
        : BetaAccessDecision.DENIED_EMAIL_NOT_ALLOWED;
  }

  public void requireAllowed(String email, boolean hasAdminRole) {
    if (!evaluate(email, hasAdminRole).allowed()) {
      throw new BetaAccessException(
          BetaAccessErrorCode.AUTH_BETA_ACCESS_DENIED,
          "当前邮箱不在内测准入名单中。");
    }
  }
}
