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

  /**
   * 对一次 AI run 做统一准入检查，并返回后续 Agent/trace 需要携带的治理上下文。
   *
   * <p>当前流程包含：按 purpose 解析治理策略、检查功能开关和账号权限、限制请求大小、
   * 消耗用户每日共享额度、获取用户级并发锁、写入准入审计记录，最后把 admissionId、
   * lockToken、策略版本等信息合并到 metadata。调用方应把返回的 metadata 继续传给
   * Agent run，便于后续释放锁、排查问题和关联治理审计。</p>
   */
  @Transactional
  public AiRunAdmission admit(AiRunContext context) {
    AiPurposePolicy policy = policyResolver.resolve(context.purpose());
    Map<String, Object> metadata = baseMetadata(context, policy);
    // 先执行不产生外部占用的静态校验：功能开关、登录态、权限和请求体大小。
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

    // 当前额度按用户维度共享，不区分 learning chat、plan draft 等具体 purpose。
    long userId = context.actor().userId();
    LocalDate quotaDate = LocalDate.now(properties.getQuotaZone());
    if (!usageStore.tryConsumeRequest(userId, quotaDate, SHARED_QUOTA_SCOPE, policy.dailyRequestLimit())) {
      reject(context, AiGovernanceErrorCode.AI_QUOTA_EXCEEDED, AiRunStatus.REJECTED_QUOTA, metadata);
    }

    /*
     * 获取用户级 AI run 锁，避免同一用户并发启动多个 AI 任务。
     * 锁 token 会随 admission metadata 下传到 Agent run，最终由运行结束回调释放。
     */
    AgentRunLockToken lockToken = runLockService.tryAcquire(userId, context.runId(), metadata)
        .orElseThrow(() -> exception(
            AiGovernanceErrorCode.AI_CONCURRENT_RUN_CONFLICT,
            AiRunStatus.REJECTED_CONCURRENT,
            metadata));
    Long admissionId;
    try {
      // 只有真正准入的请求会走到这里；被拒绝的请求已在 reject(...) 中写入 rejected 审计记录。
      admissionId = admissionRepository.insert(context, AiRunStatus.ADMITTED, null);
    } catch (RuntimeException ex) {
      // 审计落库失败时释放刚获取的并发锁，避免用户后续请求被遗留锁阻塞。
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
    // 拒绝也要落审计记录，便于统计、排查和向 API 层返回稳定错误码。
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
    // 这部分 metadata 会一路传入 Agent request、SSE 事件和 trace 快照，用于跨层关联。
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
