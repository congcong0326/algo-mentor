package org.congcong.algomentor.ai.governance.model;

/**
 * 跨 API、Agent metadata、持久化和指标使用的 AI 治理 metadata key。
 */
public final class AiGovernanceMetadataKeys {

  public static final String ADMISSION = "aiAdmission";
  public static final String RUN_ID = "aiRunId";
  public static final String ADMISSION_ID = "aiAdmissionId";
  public static final String USER_ID = "aiUserId";
  public static final String PURPOSE = "aiPurpose";
  public static final String SOURCE = "aiSource";
  public static final String QUOTA_SCOPE = "aiQuotaScope";
  public static final String DAILY_LIMIT = "aiDailyLimit";
  public static final String SYSTEM_POLICY_VERSION = "aiSystemPolicyVersion";
  public static final String GOVERNANCE_STATUS = "aiGovernanceStatus";

  private AiGovernanceMetadataKeys() {
  }
}
