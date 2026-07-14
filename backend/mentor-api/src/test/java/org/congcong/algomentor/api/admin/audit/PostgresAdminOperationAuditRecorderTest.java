package org.congcong.algomentor.api.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.congcong.algomentor.common.admin.audit.AdminAuditAction;
import org.congcong.algomentor.common.admin.audit.AdminAuditMetadataKey;
import org.congcong.algomentor.common.admin.audit.AdminAuditTargetType;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.trace.RequestTraceContext;
import org.junit.jupiter.api.Test;

class PostgresAdminOperationAuditRecorderTest {

  @Test
  void attachesRequestIdAndSerializesControlledMetadata() {
    AdminOperationAuditWriteExecutor executor = mock(AdminOperationAuditWriteExecutor.class);
    PostgresAdminOperationAuditRecorder recorder = new PostgresAdminOperationAuditRecorder(
        executor,
        new ObjectMapper(),
        null);
    AdminOperationAuditEvent event = AdminOperationAuditEvent.success(
        1L,
        AdminAuditAction.BETA_ACCESS_SETTING_UPDATE,
        AdminAuditTargetType.BETA_ACCESS_SETTINGS,
        "1",
        Map.of(AdminAuditMetadataKey.EMAIL_ALLOWLIST_ENABLED, true));

    try (RequestTraceContext.RequestTraceScope ignored = RequestTraceContext.withRequestId("request-1")) {
      recorder.record(event);
    }

    verify(executor).write(
        eq(event),
        eq("request-1"),
        eq("{\"emailAllowlistEnabled\":true}"));
  }

  @Test
  void writeAndCommitFailuresAreCountedWithoutEscaping() {
    AdminOperationAuditWriteExecutor executor = mock(AdminOperationAuditWriteExecutor.class);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PostgresAdminOperationAuditRecorder recorder = new PostgresAdminOperationAuditRecorder(
        executor,
        new ObjectMapper(),
        registry);
    AdminOperationAuditEvent event = AdminOperationAuditEvent.failure(
        1L,
        AdminAuditAction.USER_PASSWORD_RESET,
        AdminAuditTargetType.USER,
        "42",
        "AUTH_PASSWORD_RESET_FAILED");
    doThrow(new IllegalStateException("commit failed"))
        .when(executor).write(eq(event), eq(null), eq("{\"errorCode\":\"AUTH_PASSWORD_RESET_FAILED\"}"));

    assertThatCode(() -> recorder.record(event)).doesNotThrowAnyException();
    assertThat(registry.get(PostgresAdminOperationAuditRecorder.WRITE_FAILURES_TOTAL).counter().count())
        .isEqualTo(1.0);
  }
}
