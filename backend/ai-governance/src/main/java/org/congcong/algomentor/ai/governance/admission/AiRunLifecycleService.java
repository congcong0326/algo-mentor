package org.congcong.algomentor.ai.governance.admission;

import java.time.LocalDate;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.repository.mybatis.PostgresAiRunAdmissionRepository;
import org.congcong.algomentor.ai.governance.runlock.AiRunLockService;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;

public class AiRunLifecycleService {

  private final AiGovernanceProperties properties;
  private final PostgresAiRunAdmissionRepository admissionRepository;
  private final AiDailyUsageStore usageStore;
  private final AiRunLockService runLockService;

  public AiRunLifecycleService(
      AiGovernanceProperties properties,
      PostgresAiRunAdmissionRepository admissionRepository,
      AiDailyUsageStore usageStore,
      AiRunLockService runLockService) {
    this.properties = properties;
    this.admissionRepository = admissionRepository;
    this.usageStore = usageStore;
    this.runLockService = runLockService;
  }

  public void markRunning(AiRunAdmission admission, String provider, String model) {
    admissionRepository.updateStatus(
        admission.admissionId(),
        admission.runId(),
        AiRunStatus.RUNNING,
        null,
        AiUsage.zero(),
        provider,
        model,
        null);
  }

  public void markCompleted(AiRunAdmission admission, AiUsage usage, String provider, String model) {
    finish(admission, AiRunStatus.COMPLETED, null, usage, provider, model);
  }

  public void markFailed(
      AiRunAdmission admission,
      AiGovernanceErrorCode errorCode,
      AiUsage usage,
      String provider,
      String model) {
    finish(admission, AiRunStatus.FAILED, errorCode, usage, provider, model);
  }

  public void markCancelled(AiRunAdmission admission, AiUsage usage, String provider, String model) {
    finish(admission, AiRunStatus.CANCELLED, AiGovernanceErrorCode.AI_CANCELLED, usage, provider, model);
  }

  public void release(AiRunAdmission admission) {
    if (admission != null) {
      runLockService.release(admission.lockToken());
    }
  }

  private void finish(
      AiRunAdmission admission,
      AiRunStatus status,
      AiGovernanceErrorCode errorCode,
      AiUsage usage,
      String provider,
      String model) {
    AiUsage safeUsage = usage == null ? AiUsage.zero() : usage;
    try {
      admissionRepository.updateStatus(
          admission.admissionId(),
          admission.runId(),
          status,
          errorCode,
          safeUsage,
          provider,
          model,
          java.time.Instant.now());
      usageStore.addUsage(
          admission.userId(),
          LocalDate.now(properties.getQuotaZone()),
          admission.quotaScope(),
          safeUsage);
    } finally {
      release(admission);
    }
  }
}
