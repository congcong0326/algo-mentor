package org.congcong.algomentor.common.admin.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 低敏管理员审计事件。metadata 仅接受受控键和标量值。
 */
public record AdminOperationAuditEvent(
    long operatorUserId,
    AdminAuditAction action,
    AdminAuditTargetType targetType,
    String targetRef,
    AdminAuditOutcome outcome,
    Map<AdminAuditMetadataKey, Object> metadata
) {

  private static final int MAX_TARGET_REF_LENGTH = 160;
  private static final int MAX_METADATA_STRING_LENGTH = 160;

  public AdminOperationAuditEvent {
    if (operatorUserId < 1) {
      throw new IllegalArgumentException("operatorUserId must be positive.");
    }
    action = Objects.requireNonNull(action, "action");
    targetType = Objects.requireNonNull(targetType, "targetType");
    outcome = Objects.requireNonNull(outcome, "outcome");
    if (targetRef != null && targetRef.length() > MAX_TARGET_REF_LENGTH) {
      throw new IllegalArgumentException("targetRef is too long.");
    }
    metadata = validatedMetadata(metadata);
  }

  public static AdminOperationAuditEvent success(
      long operatorUserId,
      AdminAuditAction action,
      AdminAuditTargetType targetType,
      String targetRef,
      Map<AdminAuditMetadataKey, Object> metadata
  ) {
    return new AdminOperationAuditEvent(
        operatorUserId,
        action,
        targetType,
        targetRef,
        AdminAuditOutcome.SUCCESS,
        metadata);
  }

  public static AdminOperationAuditEvent failure(
      long operatorUserId,
      AdminAuditAction action,
      AdminAuditTargetType targetType,
      String targetRef,
      String errorCode
  ) {
    return new AdminOperationAuditEvent(
        operatorUserId,
        action,
        targetType,
        targetRef,
        AdminAuditOutcome.FAILURE,
        Map.of(AdminAuditMetadataKey.ERROR_CODE, errorCode));
  }

  private static Map<AdminAuditMetadataKey, Object> validatedMetadata(
      Map<AdminAuditMetadataKey, Object> source
  ) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    Map<AdminAuditMetadataKey, Object> copy = new LinkedHashMap<>();
    source.forEach((key, value) -> {
      Objects.requireNonNull(key, "metadata key");
      if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
        throw new IllegalArgumentException("Audit metadata values must be scalar.");
      }
      if (value instanceof String text && text.length() > MAX_METADATA_STRING_LENGTH) {
        throw new IllegalArgumentException("Audit metadata string is too long.");
      }
      copy.put(key, value);
    });
    return Map.copyOf(copy);
  }
}
