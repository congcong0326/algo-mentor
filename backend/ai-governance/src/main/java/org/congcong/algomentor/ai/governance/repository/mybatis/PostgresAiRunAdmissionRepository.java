package org.congcong.algomentor.ai.governance.repository.mybatis;

import java.time.Instant;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiRunAdmissionRow;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiRunStatusUpdate;

public class PostgresAiRunAdmissionRepository {

  private final AiRunAdmissionMapper mapper;

  public PostgresAiRunAdmissionRepository(AiRunAdmissionMapper mapper) {
    this.mapper = mapper;
  }

  public Long insert(AiRunContext context, AiRunStatus status, AiGovernanceErrorCode rejectionCode) {
    AiRunAdmissionRow row = new AiRunAdmissionRow(
        null,
        context.runId(),
        context.actor().userId(),
        context.purpose(),
        context.source(),
        status,
        context.idempotencyKey(),
        context.requestSize(),
        rejectionCode == null ? null : rejectionCode.name(),
        null,
        null,
        null,
        0,
        0,
        0,
        0,
        0,
        null,
        terminal(status) ? Instant.now() : null,
        null,
        null);
    mapper.insertAdmission(row);
    return row.id();
  }

  public Long insertRejected(
      String runId,
      Long userId,
      AiPurpose purpose,
      AiRunSource source,
      String idempotencyKey,
      int requestSize,
      AiRunStatus status,
      AiGovernanceErrorCode rejectionCode) {
    AiRunAdmissionRow row = new AiRunAdmissionRow(
        null,
        runId,
        userId,
        purpose,
        source,
        status,
        idempotencyKey,
        requestSize,
        rejectionCode == null ? null : rejectionCode.name(),
        null,
        null,
        null,
        0,
        0,
        0,
        0,
        0,
        null,
        Instant.now(),
        null,
        null);
    mapper.insertAdmission(row);
    return row.id();
  }

  public void updateStatus(
      Long admissionId,
      String runId,
      AiRunStatus status,
      AiGovernanceErrorCode errorCode,
      AiUsage usage,
      String provider,
      String model,
      Instant completedAt) {
    mapper.updateStatus(new AiRunStatusUpdate(
        admissionId,
        runId,
        status,
        errorCode == null ? null : errorCode.name(),
        provider,
        model,
        usage == null ? AiUsage.zero() : usage,
        completedAt));
  }

  private static boolean terminal(AiRunStatus status) {
    return switch (status) {
      case COMPLETED, FAILED, CANCELLED, EXPIRED, REJECTED_QUOTA, REJECTED_CONCURRENT,
          REJECTED_DISABLED, REJECTED_UNAUTHENTICATED, REJECTED_FORBIDDEN,
          REJECTED_REQUEST_TOO_LARGE -> true;
      case ADMITTED, RUNNING -> false;
    };
  }
}
