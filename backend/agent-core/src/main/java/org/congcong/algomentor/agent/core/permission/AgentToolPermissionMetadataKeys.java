package org.congcong.algomentor.agent.core.permission;

/**
 * Agent 工具权限流程共享 metadata 字段名。
 */
public final class AgentToolPermissionMetadataKeys {

  /**
   * 单次权限请求 ID。
   */
  public static final String PERMISSION_REQUEST_ID = "permissionRequestId";

  /**
   * 权限行为：allow、deny、ask 或 passthrough。
   */
  public static final String PERMISSION_BEHAVIOR = "permissionBehavior";

  /**
   * 用户或系统最终决策。
   */
  public static final String PERMISSION_DECISION = "permissionDecision";

  /**
   * 权限决策原因。
   */
  public static final String PERMISSION_DECISION_REASON = "permissionDecisionReason";

  /**
   * 权限请求是否超时。
   */
  public static final String PERMISSION_TIMEOUT = "permissionTimeout";

  /**
   * 权限等待耗时，单位毫秒。
   */
  public static final String PERMISSION_LATENCY_MS = "permissionLatencyMs";

  /**
   * 产生权限策略结果的来源。
   */
  public static final String PERMISSION_POLICY_SOURCE = "permissionPolicySource";

  /**
   * 产生权限策略结果的 hook 名称。
   */
  public static final String PERMISSION_HOOK_NAME = "permissionHookName";

  /**
   * 权限 hook 失败时记录的低敏错误类型。
   */
  public static final String PERMISSION_HOOK_ERROR_TYPE = "permissionHookErrorType";

  /**
   * 权限请求所属用户 ID。
   */
  public static final String PERMISSION_OWNER_USER_ID = "permissionOwnerUserId";

  private AgentToolPermissionMetadataKeys() {
  }
}
