package org.congcong.algomentor.common.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminOperationAuditEventTest {

  @Test
  void acceptsOnlyControlledScalarMetadata() {
    AdminOperationAuditEvent event = AdminOperationAuditEvent.success(
        1L,
        AdminAuditAction.BETA_ACCESS_SETTING_UPDATE,
        AdminAuditTargetType.BETA_ACCESS_SETTINGS,
        "1",
        Map.of(AdminAuditMetadataKey.EMAIL_ALLOWLIST_ENABLED, true));

    assertThat(event.metadata()).containsEntry(AdminAuditMetadataKey.EMAIL_ALLOWLIST_ENABLED, true);
    assertThat(AdminAuditMetadataKey.values())
        .extracting(AdminAuditMetadataKey::value)
        .noneMatch(key -> key.toLowerCase().matches(".*(password|authorization|token|prompt|sourcecode|usercode).*"));
  }

  @Test
  void rejectsStructuredOrOversizedMetadata() {
    assertThatThrownBy(() -> AdminOperationAuditEvent.success(
        1L,
        AdminAuditAction.BETA_ALLOWED_EMAIL_ADD,
        AdminAuditTargetType.BETA_ALLOWED_EMAIL,
        "7",
        Map.of(AdminAuditMetadataKey.ERROR_CODE, Map.of("nested", true))))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> AdminOperationAuditEvent.failure(
        1L,
        AdminAuditAction.USER_PASSWORD_RESET,
        AdminAuditTargetType.USER,
        "42",
        "x".repeat(161)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
