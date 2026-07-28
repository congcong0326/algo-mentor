package org.congcong.algomentor.common.admin.audit;

/**
 * 审计目标类型；targetRef 只保存对应实体 ID。
 */
public enum AdminAuditTargetType {
  BETA_ACCESS_SETTINGS,
  BETA_ALLOWED_EMAIL,
  USER,
  AUTH_SESSION,
  AI_RUNTIME_SETTINGS,
  AI_USER_POLICY,
  AI_MODEL_PRICE,
  AI_RUN,
  USER_GROUP,
  GENERIC_POLICY,
  DATABASE_BACKUP
}
