package org.congcong.algomentor.ai.governance.admission;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.repository.mybatis.PostgresAiRunAdmissionRepository;
import org.congcong.algomentor.ai.governance.runlock.AiRunLockService;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

public class AiRunAdmissionService {

  public static final String SHARED_QUOTA_SCOPE = "ALL";

  private final AiGovernanceProperties properties;
  private final AiPurposePolicyResolver policyResolver;
  private final AiDailyUsageStore usageStore;
  private final AiRunLockService runLockService;
  private final PostgresAiRunAdmissionRepository admissionRepository;

  public AiRunAdmissionService(
      AiGovernanceProperties properties,
      AiPurposePolicyResolver policyResolver,
      AiDailyUsageStore usageStore,
      AiRunLockService runLockService,
      PostgresAiRunAdmissionRepository admissionRepository) {
    this.properties = properties;
    this.policyResolver = policyResolver;
    this.usageStore = usageStore;
    this.runLockService = runLockService;
    this.admissionRepository = admissionRepository;
  }

  @Transactional
  public AiRunAdmission admit(AiRunContext context) {
    AiPurposePolicy policy = policyResolver.resolve(context.purpose());
    Map<String, Object> metadata = baseMetadata(context, policy);
    if (!properties.isEnabled() || !policy.enabled()) {
      reject(context, AiGovernanceErrorCode.AI_PURPOSE_DISABLED, AiRunStatus.REJECTED_DISABLED, metadata);
    }
    if (!context.actor().authenticated()) {
      reject(context, AiGovernanceErrorCode.AI_UNAUTHENTICATED, AiRunStatus.REJECTED_UNAUTHENTICATED, metadata);
    }
    if (policy.adminOnly() && !context.actor().admin()) {
      reject(context, AiGovernanceErrorCode.AI_FORBIDDEN, AiRunStatus.REJECTED_FORBIDDEN, metadata);
    }
    if (context.requestSize() > policy.maxRequestBytes()) {
      reject(context, AiGovernanceErrorCode.AI_REQUEST_TOO_LARGE, AiRunStatus.REJECTED_REQUEST_TOO_LARGE, metadata);
    }

    long userId = context.actor().userId();
    LocalDate quotaDate = LocalDate.now(properties.getQuotaZone());
    if (!usageStore.tryConsumeRequest(userId, quotaDate, SHARED_QUOTA_SCOPE, policy.dailyRequestLimit())) {
      reject(context, AiGovernanceErrorCode.AI_QUOTA_EXCEEDED, AiRunStatus.REJECTED_QUOTA, metadata);
    }

    AgentRunLockToken lockToken = runLockService.tryAcquire(userId, context.runId(), metadata)
        .orElseThrow(() -> exception(
            AiGovernanceErrorCode.AI_CONCURRENT_RUN_CONFLICT,
            AiRunStatus.REJECTED_CONCURRENT,
            metadata));
    Long admissionId;
    try {
      admissionId = admissionRepository.insert(context, AiRunStatus.ADMITTED, null);
    } catch (RuntimeException ex) {
      runLockService.release(lockToken);
      throw ex;
    }
    metadata.put(AiGovernanceMetadataKeys.ADMISSION_ID, admissionId);
    metadata.put(AiGovernanceMetadataKeys.GOVERNANCE_STATUS, AiRunStatus.ADMITTED.name());
    AiRunAdmission admission = new AiRunAdmission(
        admissionId,
        context.runId(),
        userId,
        context.purpose(),
        context.source(),
        AiRunStatus.ADMITTED,
        SHARED_QUOTA_SCOPE,
        lockToken,
        policy,
        metadata,
        Instant.now());
    metadata.put(AiGovernanceMetadataKeys.ADMISSION, admission);
    return new AiRunAdmission(
        admission.admissionId(),
        admission.runId(),
        admission.userId(),
        admission.purpose(),
        admission.source(),
        admission.status(),
        admission.quotaScope(),
        admission.lockToken(),
        admission.policy(),
        metadata,
        admission.admittedAt());
  }

  private void reject(
      AiRunContext context,
      AiGovernanceErrorCode code,
      AiRunStatus status,
      Map<String, Object> metadata) {
    admissionRepository.insertRejected(
        context.runId(),
        context.actor().userId(),
        context.purpose(),
        context.source(),
        context.idempotencyKey(),
        context.requestSize(),
        status,
        code);
    throw exception(code, status, metadata);
  }

  private Map<String, Object> baseMetadata(AiRunContext context, AiPurposePolicy policy) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(AiGovernanceMetadataKeys.RUN_ID, context.runId());
    if (context.actor().userId() != null) {
      metadata.put(AiGovernanceMetadataKeys.USER_ID, context.actor().userId());
    }
    metadata.put(AiGovernanceMetadataKeys.PURPOSE, context.purpose().name());
    metadata.put(AiGovernanceMetadataKeys.SOURCE, context.source().name());
    metadata.put(AiGovernanceMetadataKeys.QUOTA_SCOPE, SHARED_QUOTA_SCOPE);
    metadata.put(AiGovernanceMetadataKeys.DAILY_LIMIT, policy.dailyRequestLimit());
    metadata.put(AiGovernanceMetadataKeys.SYSTEM_POLICY_VERSION, policy.systemPolicyVersion());
    metadata.putAll(context.metadata());
    return metadata;
  }

  private AiRunAdmissionException exception(
      AiGovernanceErrorCode code,
      AiRunStatus status,
      Map<String, Object> metadata) {
    return new AiRunAdmissionException(code, status, message(code), httpStatus(code), metadata);
  }

  private static String message(AiGovernanceErrorCode code) {
    return switch (code) {
      case AI_PURPOSE_DISABLED, AI_PROVIDER_DISABLED -> "AI 功能暂未开放。";
      case AI_UNAUTHENTICATED -> "当前请求未登录或无法解析当前用户。";
      case AI_FORBIDDEN -> "当前账号无权使用该 AI 功能。";
      case AI_QUOTA_EXCEEDED -> "今日 AI 使用次数已达上限，请明天再试。";
      case AI_CONCURRENT_RUN_CONFLICT -> "已有一个 AI 任务正在运行，请等待完成后再试。";
      case AI_REQUEST_TOO_LARGE -> "请求内容过长，请精简后再试。";
      default -> "AI 服务暂时不可用，请稍后再试。";
    };
  }

  private static HttpStatus httpStatus(AiGovernanceErrorCode code) {
    return switch (code) {
      case AI_UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
      case AI_FORBIDDEN, AI_PURPOSE_DISABLED -> HttpStatus.FORBIDDEN;
      case AI_QUOTA_EXCEEDED, AI_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
      case AI_CONCURRENT_RUN_CONFLICT -> HttpStatus.CONFLICT;
      case AI_REQUEST_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
      case AI_PROVIDER_DISABLED, AI_PROVIDER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case AI_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
      case AI_STRUCTURED_OUTPUT_INVALID -> HttpStatus.BAD_GATEWAY;
      case AI_CANCELLED -> HttpStatus.BAD_REQUEST;
      case AI_UNKNOWN -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
