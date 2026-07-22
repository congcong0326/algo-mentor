package org.congcong.algomentor.ai.governance.policy.runtime;

import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiRuntimeSettingsMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.AiUserPolicyMapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiRuntimeSettingsRow;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiUserPolicyRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 负责每次请求从数据库解析动态全局策略和用户覆盖。 */
public class AiRuntimePolicyService {

  private static final Logger log = LoggerFactory.getLogger(AiRuntimePolicyService.class);

  private final AiGovernanceProperties properties;
  private final AiRuntimeSettingsMapper settingsMapper;
  private final AiUserPolicyMapper userPolicyMapper;
  private final AiRuntimeCache runtimeCache;

  public AiRuntimePolicyService(
      AiGovernanceProperties properties,
      AiRuntimeSettingsMapper settingsMapper,
      AiUserPolicyMapper userPolicyMapper
  ) {
    this(properties, settingsMapper, userPolicyMapper, null);
  }

  public AiRuntimePolicyService(
      AiGovernanceProperties properties,
      AiRuntimeSettingsMapper settingsMapper,
      AiUserPolicyMapper userPolicyMapper,
      AiRuntimeCache runtimeCache
  ) {
    this.properties = properties;
    this.settingsMapper = settingsMapper;
    this.userPolicyMapper = userPolicyMapper;
    this.runtimeCache = runtimeCache;
  }

  public AiRuntimeSettings currentSettings(AiPurposePolicy staticPolicy) {
    try {
      java.util.Optional<AiRuntimeSettings> loaded = runtimeCache == null
          ? loadSettings()
          : runtimeCache.getSettings(this::loadSettings);
      if (loaded.isPresent()) {
        return loaded.get();
      }
      log.error("AI runtime settings row is missing; using static daily limit fallback");
      return new AiRuntimeSettings(true, staticPolicy.dailyRequestLimit(), null, null);
    } catch (RuntimeException exception) {
      throw new AiRuntimePolicyException(
          AiGovernanceErrorCode.AI_RUNTIME_SETTINGS_INVALID,
          "Failed to load AI runtime settings.",
          exception);
    }
  }

  public AiUserPolicy userPolicy(long userId) {
    try {
      return runtimeCache == null
          ? loadUserPolicy(userId)
          : runtimeCache.getUserPolicy(userId, () -> loadUserPolicy(userId));
    } catch (RuntimeException exception) {
      throw new AiRuntimePolicyException(
          AiGovernanceErrorCode.AI_USER_POLICY_INVALID,
          "Failed to load AI user policy.",
          exception);
    }
  }

  private java.util.Optional<AiRuntimeSettings> loadSettings() {
    AiRuntimeSettingsRow row = settingsMapper.findSingleton();
    return row == null ? java.util.Optional.empty() : java.util.Optional.of(row.toDomain());
  }

  private AiUserPolicy loadUserPolicy(long userId) {
    AiUserPolicyRow row = userPolicyMapper.findByUserId(userId);
    return row == null ? AiUserPolicy.inherited(userId) : row.toDomain();
  }

  public EffectiveAiRuntimePolicy resolve(AiPurposePolicy staticPolicy, long userId) {
    AiRuntimeSettings settings = currentSettings(staticPolicy);
    AiUserPolicy userPolicy = userPolicy(userId);
    AiRuntimeDisabledReason disabledReason = disabledReason(settings, userPolicy, staticPolicy);
    int effectiveLimit = userPolicy.dailyRequestLimitOverride() == null
        ? settings.defaultDailyRequestLimit()
        : userPolicy.dailyRequestLimitOverride();
    Long updatedBy = userPolicy.updatedBy() == null ? settings.updatedBy() : userPolicy.updatedBy();
    java.time.Instant updatedAt = userPolicy.updatedAt() == null ? settings.updatedAt() : userPolicy.updatedAt();
    return new EffectiveAiRuntimePolicy(
        settings.aiEnabled(),
        userPolicy.aiEnabledOverride(),
        disabledReason == null,
        disabledReason,
        settings.defaultDailyRequestLimit(),
        userPolicy.dailyRequestLimitOverride(),
        effectiveLimit,
        updatedBy,
        updatedAt);
  }

  private AiRuntimeDisabledReason disabledReason(
      AiRuntimeSettings settings,
      AiUserPolicy userPolicy,
      AiPurposePolicy staticPolicy
  ) {
    if (!properties.isEnabled()) {
      return AiRuntimeDisabledReason.DEPLOYMENT;
    }
    if (!settings.aiEnabled()) {
      return AiRuntimeDisabledReason.GLOBAL;
    }
    if (!staticPolicy.enabled()) {
      return AiRuntimeDisabledReason.PURPOSE;
    }
    if (Boolean.FALSE.equals(userPolicy.aiEnabledOverride())) {
      return AiRuntimeDisabledReason.USER;
    }
    return null;
  }
}
