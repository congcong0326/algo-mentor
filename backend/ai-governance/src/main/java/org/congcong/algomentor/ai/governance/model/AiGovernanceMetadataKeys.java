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
  /** 调用级台账使用的稳定调用类型。 */
  public static final String CALL_KIND = "aiCallKind";
  /** 调用级台账的唯一调用 ID。 */
  public static final String CALL_ID = "aiCallId";
  /** 业务执行解析到的稳定场景 code。 */
  public static final String SCENARIO_CODE = "aiScenarioCode";
  /** 模型路由策略的稳定主键。 */
  public static final String MODEL_ROUTE_POLICY_ID = "aiModelRoutePolicyId";
  /** 模型路由策略版本。 */
  public static final String MODEL_ROUTE_POLICY_VERSION = "aiModelRoutePolicyVersion";
  /** 模型路由命中来源。 */
  public static final String MODEL_ROUTE_MATCH_SOURCE = "aiModelRouteMatchSource";
  /** 模型路由命中的用户或用户组主键。 */
  public static final String MODEL_ROUTE_MATCHED_SUBJECT_ID = "aiModelRouteMatchedSubjectId";
  /** 管理员显式维护的模型主键。 */
  public static final String CONFIGURED_MODEL_ID = "aiConfiguredModelId";
  /** 管理员维护的 provider instance 主键。 */
  public static final String PROVIDER_INSTANCE_ID = "aiProviderInstanceId";
  /** 代码注册的 provider 类型。 */
  public static final String PROVIDER_TYPE = "aiProviderType";
  /** 调用上游时使用的真实模型标识。 */
  public static final String UPSTREAM_MODEL_ID = "aiUpstreamModelId";
  /** provider instance 的低敏配置版本标识。 */
  public static final String PROVIDER_CONFIG_REVISION = "aiProviderConfigRevision";

  private AiGovernanceMetadataKeys() {
  }
}
