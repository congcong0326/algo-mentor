package org.congcong.algomentor.ai.governance.policy.runtime;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiRuntimeSettingsMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiUserPolicyMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiRuntimeSettingsRow;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiUserPolicyRow;
import org.congcong.algomentor.common.admin.audit.AdminAuditAction;
import org.congcong.algomentor.common.admin.audit.AdminAuditMetadataKey;
import org.congcong.algomentor.common.admin.audit.AdminAuditTargetType;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.springframework.transaction.annotation.Transactional;

/** 管理员对全局 AI 策略和单用户覆盖的写入服务。 */
public class AiRuntimeAdminService {

  private final AiRuntimePolicyService runtimePolicyService;
  private final AiRuntimeSettingsMapper settingsMapper;
  private final AiUserPolicyMapper userPolicyMapper;
  private final IdentityUserRepository identityUserRepository;
  private final AdminOperationAuditRecorder auditRecorder;
  private final Clock clock;
  private final AiRuntimeCache runtimeCache;

  public AiRuntimeAdminService(
      AiRuntimePolicyService runtimePolicyService,
      AiRuntimeSettingsMapper settingsMapper,
      AiUserPolicyMapper userPolicyMapper,
      IdentityUserRepository identityUserRepository,
      AdminOperationAuditRecorder auditRecorder,
      Clock clock
  ) {
    this(
        runtimePolicyService,
        settingsMapper,
        userPolicyMapper,
        identityUserRepository,
        auditRecorder,
        clock,
        null);
  }

  public AiRuntimeAdminService(
      AiRuntimePolicyService runtimePolicyService,
      AiRuntimeSettingsMapper settingsMapper,
      AiUserPolicyMapper userPolicyMapper,
      IdentityUserRepository identityUserRepository,
      AdminOperationAuditRecorder auditRecorder,
      Clock clock,
      AiRuntimeCache runtimeCache
  ) {
    this.runtimePolicyService = runtimePolicyService;
    this.settingsMapper = settingsMapper;
    this.userPolicyMapper = userPolicyMapper;
    this.identityUserRepository = identityUserRepository;
    this.auditRecorder = auditRecorder;
    this.clock = clock;
    this.runtimeCache = runtimeCache;
  }

  public AiRuntimeSettings getSettings(AiPurposePolicy staticPolicy) {
    return runtimePolicyService.currentSettings(staticPolicy);
  }

  public AiUserPolicy getUserPolicy(long userId) {
    return runtimePolicyService.userPolicy(userId);
  }

  @Transactional
  public AiRuntimeSettings updateSettings(
      Boolean aiEnabled,
      Integer defaultDailyRequestLimit,
      long operatorUserId,
      AiPurposePolicy staticPolicy
  ) {
    if (aiEnabled == null || !AiRuntimePolicyConstraints.isValidDailyRequestLimit(defaultDailyRequestLimit)) {
      recordFailure(
          operatorUserId,
          AdminAuditAction.AI_GLOBAL_SETTING_UPDATE,
          AdminAuditTargetType.AI_RUNTIME_SETTINGS,
          "1",
          AiGovernanceErrorCode.AI_RUNTIME_SETTINGS_INVALID);
      throw new AiRuntimePolicyException(
          AiGovernanceErrorCode.AI_RUNTIME_SETTINGS_INVALID,
          "AI runtime settings are invalid.");
    }
    Instant now = Instant.now(clock);
    try {
      int changed = settingsMapper.update(new AiRuntimeSettingsRow(
          1,
          aiEnabled,
          defaultDailyRequestLimit,
          operatorUserId,
          now));
      if (changed != 1) {
        throw new AiRuntimePolicyException(
            AiGovernanceErrorCode.AI_RUNTIME_SETTINGS_INVALID,
            "AI runtime settings row is unavailable.");
      }
      if (runtimeCache != null) {
        runtimeCache.invalidateSettings();
      }
    } catch (AiRuntimePolicyException exception) {
      recordFailure(
          operatorUserId,
          AdminAuditAction.AI_GLOBAL_SETTING_UPDATE,
          AdminAuditTargetType.AI_RUNTIME_SETTINGS,
          "1",
          exception.code());
      throw exception;
    } catch (RuntimeException exception) {
      recordFailure(
          operatorUserId,
          AdminAuditAction.AI_GLOBAL_SETTING_UPDATE,
          AdminAuditTargetType.AI_RUNTIME_SETTINGS,
          "1",
          AiGovernanceErrorCode.AI_RUNTIME_SETTINGS_INVALID);
      throw new AiRuntimePolicyException(
          AiGovernanceErrorCode.AI_RUNTIME_SETTINGS_INVALID,
          "Failed to update AI runtime settings.",
          exception);
    }
    auditRecorder.record(AdminOperationAuditEvent.success(
        operatorUserId,
        AdminAuditAction.AI_GLOBAL_SETTING_UPDATE,
        AdminAuditTargetType.AI_RUNTIME_SETTINGS,
        "1",
        Map.of(
            AdminAuditMetadataKey.GLOBAL_AI_ENABLED, aiEnabled,
            AdminAuditMetadataKey.DEFAULT_DAILY_REQUEST_LIMIT, defaultDailyRequestLimit)));
    return new AiRuntimeSettings(aiEnabled, defaultDailyRequestLimit, operatorUserId, now);
  }

  @Transactional
  public AiUserPolicy updateUserPolicy(
      long userId,
      Boolean aiEnabledOverride,
      Integer dailyRequestLimitOverride,
      long operatorUserId
  ) {
    validateTargetUser(userId, operatorUserId);
    Boolean normalizedEnabledOverride = Boolean.TRUE.equals(aiEnabledOverride) ? null : aiEnabledOverride;
    if (dailyRequestLimitOverride != null
        && !AiRuntimePolicyConstraints.isValidDailyRequestLimit(dailyRequestLimitOverride)) {
      recordFailure(
          operatorUserId,
          AdminAuditAction.AI_USER_POLICY_UPDATE,
          AdminAuditTargetType.AI_USER_POLICY,
          Long.toString(userId),
          AiGovernanceErrorCode.AI_USER_POLICY_INVALID);
      throw new AiRuntimePolicyException(
          AiGovernanceErrorCode.AI_USER_POLICY_INVALID,
          "AI user policy is invalid.");
    }
    Instant now = Instant.now(clock);
    try {
      if (normalizedEnabledOverride == null && dailyRequestLimitOverride == null) {
        userPolicyMapper.deleteByUserId(userId);
      } else {
        userPolicyMapper.upsert(new AiUserPolicyRow(
            userId,
            normalizedEnabledOverride,
            dailyRequestLimitOverride,
            operatorUserId,
            now));
      }
      if (runtimeCache != null) {
        runtimeCache.invalidateUserPolicy(userId);
      }
    } catch (RuntimeException exception) {
      recordFailure(
          operatorUserId,
          AdminAuditAction.AI_USER_POLICY_UPDATE,
          AdminAuditTargetType.AI_USER_POLICY,
          Long.toString(userId),
          AiGovernanceErrorCode.AI_USER_POLICY_INVALID);
      throw new AiRuntimePolicyException(
          AiGovernanceErrorCode.AI_USER_POLICY_INVALID,
          "Failed to update AI user policy.",
          exception);
    }
    Map<AdminAuditMetadataKey, Object> metadata = new LinkedHashMap<>();
    if (normalizedEnabledOverride != null) {
      metadata.put(AdminAuditMetadataKey.AI_ENABLED_OVERRIDE, normalizedEnabledOverride);
    }
    if (dailyRequestLimitOverride != null) {
      metadata.put(AdminAuditMetadataKey.DAILY_REQUEST_LIMIT_OVERRIDE, dailyRequestLimitOverride);
    }
    auditRecorder.record(AdminOperationAuditEvent.success(
        operatorUserId,
        AdminAuditAction.AI_USER_POLICY_UPDATE,
        AdminAuditTargetType.AI_USER_POLICY,
        Long.toString(userId),
        metadata));
    return new AiUserPolicy(
        userId,
        normalizedEnabledOverride,
        dailyRequestLimitOverride,
        normalizedEnabledOverride == null && dailyRequestLimitOverride == null ? null : operatorUserId,
        normalizedEnabledOverride == null && dailyRequestLimitOverride == null ? null : now);
  }

  private void validateTargetUser(long userId, long operatorUserId) {
    if (userId < 1 || identityUserRepository.findUserById(userId)
        .filter(user -> user.status() != AuthUserStatus.DELETED)
        .isEmpty()) {
      recordFailure(
          operatorUserId,
          AdminAuditAction.AI_USER_POLICY_UPDATE,
          AdminAuditTargetType.AI_USER_POLICY,
          userId < 1 ? null : Long.toString(userId),
          AiGovernanceErrorCode.AI_USER_POLICY_INVALID);
      throw new AiRuntimePolicyException(
          AiGovernanceErrorCode.AI_USER_POLICY_INVALID,
          "AI user policy target user is unavailable.");
    }
  }

  private void recordFailure(
      long operatorUserId,
      AdminAuditAction action,
      AdminAuditTargetType targetType,
      String targetRef,
      AiGovernanceErrorCode errorCode
  ) {
    auditRecorder.record(AdminOperationAuditEvent.failure(
        operatorUserId,
        action,
        targetType,
        targetRef,
        errorCode.name()));
  }
}
