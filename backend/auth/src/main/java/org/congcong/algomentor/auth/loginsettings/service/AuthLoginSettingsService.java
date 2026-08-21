package org.congcong.algomentor.auth.loginsettings.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.loginsettings.model.AuthLoginSettings;
import org.congcong.algomentor.auth.loginsettings.repository.AuthLoginSettingsRepository;
import org.congcong.algomentor.common.admin.audit.AdminAuditAction;
import org.congcong.algomentor.common.admin.audit.AdminAuditMetadataKey;
import org.congcong.algomentor.common.admin.audit.AdminAuditTargetType;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.springframework.transaction.annotation.Transactional;

/** 数据库是认证入口开关的唯一运行时来源；首次读取时从部署默认值初始化。 */
public class AuthLoginSettingsService implements AuthLoginSettingsProvider {

  private static final String SETTINGS_TARGET_REF = "1";

  private final AuthLoginSettingsRepository repository;
  private final AuthProperties fallbackProperties;
  private final AdminOperationAuditRecorder auditRecorder;
  private final Clock clock;

  public AuthLoginSettingsService(
      AuthLoginSettingsRepository repository,
      AuthProperties fallbackProperties,
      AdminOperationAuditRecorder auditRecorder,
      Clock clock
  ) {
    this.repository = repository;
    this.fallbackProperties = fallbackProperties;
    this.auditRecorder = auditRecorder;
    this.clock = clock;
  }

  @Override
  public AuthLoginSettings current() {
    return repository.findSettings().orElseGet(this::initializeFromProperties);
  }

  @Transactional
  public AuthLoginSettings update(
      Boolean accountRegistrationEnabled,
      Boolean passwordLoginEnabled,
      Boolean passwordRegistrationEnabled,
      Boolean googleLoginEnabled,
      Boolean githubLoginEnabled,
      long operatorUserId
  ) {
    current();
    boolean accountRegistration = requireValue(accountRegistrationEnabled, "账号注册开关不能为空。");
    boolean passwordLogin = requireValue(passwordLoginEnabled, "密码登录开关不能为空。");
    boolean passwordRegistration = requireValue(passwordRegistrationEnabled, "密码注册开关不能为空。");
    boolean googleLogin = requireValue(googleLoginEnabled, "Google 登录开关不能为空。");
    boolean githubLogin = requireValue(githubLoginEnabled, "GitHub 登录开关不能为空。");
    Instant now = Instant.now(clock);
    if (!repository.updateSettings(
        accountRegistration,
        passwordLogin,
        passwordRegistration,
        googleLogin,
        githubLogin,
        operatorUserId,
        now)) {
      throw new AuthLoginSettingsException(
          AuthLoginSettingsErrorCode.AUTH_LOGIN_SETTINGS_CONFLICT,
          "登录设置不存在或更新冲突。");
    }
    auditRecorder.record(AdminOperationAuditEvent.success(
        operatorUserId,
        AdminAuditAction.AUTH_LOGIN_SETTING_UPDATE,
        AdminAuditTargetType.AUTH_LOGIN_SETTINGS,
        SETTINGS_TARGET_REF,
        Map.of(
            AdminAuditMetadataKey.ACCOUNT_REGISTRATION_ENABLED, accountRegistration,
            AdminAuditMetadataKey.PASSWORD_LOGIN_ENABLED, passwordLogin,
            AdminAuditMetadataKey.PASSWORD_REGISTRATION_ENABLED, passwordRegistration,
            AdminAuditMetadataKey.GOOGLE_LOGIN_ENABLED, googleLogin,
            AdminAuditMetadataKey.GITHUB_LOGIN_ENABLED, githubLogin)));
    return current();
  }

  private AuthLoginSettings initializeFromProperties() {
    repository.insertIfAbsent(AuthLoginSettings.fromProperties(fallbackProperties, Instant.now(clock)));
    return repository.findSettings().orElseThrow(() -> new AuthLoginSettingsException(
        AuthLoginSettingsErrorCode.AUTH_LOGIN_SETTINGS_CONFLICT,
        "登录设置初始化失败。"));
  }

  private static boolean requireValue(Boolean value, String message) {
    if (value == null) {
      throw new AuthLoginSettingsException(AuthLoginSettingsErrorCode.AUTH_LOGIN_SETTINGS_INVALID, message);
    }
    return value;
  }
}
