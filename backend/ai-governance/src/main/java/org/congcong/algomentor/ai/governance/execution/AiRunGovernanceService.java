package org.congcong.algomentor.ai.governance.execution;

import java.util.LinkedHashMap;
import java.util.Map;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionException;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.model.AiActor;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimeDisabledReason;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimePolicyService;
import org.congcong.algomentor.ai.governance.policy.runtime.EffectiveAiRuntimePolicy;
import org.congcong.algomentor.ai.governance.routing.AiModelRouteException;
import org.congcong.algomentor.ai.governance.routing.AiModelRouteResolver;
import org.congcong.algomentor.ai.governance.routing.AiRunInvocationTargetStore;
import org.congcong.algomentor.ai.governance.routing.ResolvedAiModelSnapshot;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;
import org.springframework.http.HttpStatus;

/**
 * 为 Runtime 与直接调用提供统一的治理租约。
 *
 * <p>用户入口委托完整准入服务；子调用与后台调用只执行动态开关检查及独立模型路由，
 * 不消耗共享额度，也不持有用户级运行锁。</p>
 */
public class AiRunGovernanceService {

  private final AiRunAdmissionService admissionService;
  private final AiRunLifecycleService lifecycleService;
  private final AiPurposePolicyResolver policyResolver;
  private final AiRuntimePolicyService runtimePolicyService;
  private final AiModelRouteResolver modelRouteResolver;
  private final AiRunInvocationTargetStore invocationTargetStore;

  public AiRunGovernanceService(
      AiRunAdmissionService admissionService,
      AiRunLifecycleService lifecycleService,
      AiPurposePolicyResolver policyResolver,
      AiRuntimePolicyService runtimePolicyService,
      AiModelRouteResolver modelRouteResolver,
      AiRunInvocationTargetStore invocationTargetStore
  ) {
    this.admissionService = admissionService;
    this.lifecycleService = lifecycleService;
    this.policyResolver = policyResolver;
    this.runtimePolicyService = runtimePolicyService;
    this.modelRouteResolver = modelRouteResolver;
    this.invocationTargetStore = invocationTargetStore;
  }

  public AiRunGovernanceLease begin(AiRunGovernanceRequest request) {
    if (request.mode() == AiRunGovernanceMode.USER_ENTRY) {
      return beginUserEntry(request);
    }
    return beginNonUser(request);
  }

  /** 仅检查动态可用性，不获取锁、消耗额度或绑定调用目标。 */
  public boolean isAllowed(AiRunGovernanceRequest request) {
    try {
      assertDynamicEnabled(request);
      return true;
    } catch (AiRunAdmissionException exception) {
      return false;
    }
  }

  private AiRunGovernanceLease beginUserEntry(AiRunGovernanceRequest request) {
    AiRunAdmission admission = admissionService.admit(new AiRunContext(
        request.runId(),
        new AiActor(request.userId(), java.util.Set.of(AuthRole.USER), true),
        request.purpose(),
        request.source(),
        request.idempotencyKey(),
        request.requestSize(),
        request.streaming(),
        request.metadata(),
        null));
    try {
      LlmInvocationTarget target = admittedTarget(request.runId());
      Map<String, Object> metadata = metadata(request, admission.metadata(), null);
      lifecycleService.markRunning(admission, null, null);
      return new AiRunGovernanceLease(
          request,
          admission,
          target,
          metadata,
          new AiRunGovernanceLease.Settlement() {
            @Override
            public void complete(AiUsage usage, String provider, String model) {
              try {
                lifecycleService.markCompleted(admission, usage, provider, model);
              } finally {
                removeInvocationTarget(request.runId());
              }
            }

            @Override
            public void fail(AiGovernanceErrorCode errorCode, AiUsage usage, String provider, String model) {
              try {
                lifecycleService.markFailed(admission, errorCode, usage, provider, model);
              } finally {
                removeInvocationTarget(request.runId());
              }
            }

            @Override
            public void cancel(AiUsage usage, String provider, String model) {
              try {
                lifecycleService.markCancelled(admission, usage, provider, model);
              } finally {
                removeInvocationTarget(request.runId());
              }
            }
          });
    } catch (RuntimeException exception) {
      try {
        lifecycleService.markFailed(admission, errorCode(exception), AiUsage.zero(), null, null);
      } finally {
        removeInvocationTarget(request.runId());
      }
      throw exception;
    }
  }

  private AiRunGovernanceLease beginNonUser(AiRunGovernanceRequest request) {
    assertDynamicEnabled(request);
    ResolvedAiModelSnapshot snapshot = resolveSnapshot(request);
    bindInvocationTarget(request.runId(), snapshot.invocationTarget());
    return new AiRunGovernanceLease(
        request,
        null,
        snapshot.invocationTarget(),
        metadata(request, null, snapshot),
        new AiRunGovernanceLease.Settlement() {
          @Override
          public void complete(AiUsage usage, String provider, String model) {
            removeInvocationTarget(request.runId());
          }

          @Override
          public void fail(AiGovernanceErrorCode errorCode, AiUsage usage, String provider, String model) {
            removeInvocationTarget(request.runId());
          }

          @Override
          public void cancel(AiUsage usage, String provider, String model) {
            removeInvocationTarget(request.runId());
          }
        });
  }

  private LlmInvocationTarget admittedTarget(String runId) {
    if (invocationTargetStore == null) {
      throw routeNotConfigured();
    }
    return invocationTargetStore.find(runId).orElseThrow(this::routeNotConfigured);
  }

  private ResolvedAiModelSnapshot resolveSnapshot(AiRunGovernanceRequest request) {
    if (modelRouteResolver == null) {
      throw routeNotConfigured();
    }
    return request.source().businessScenario()
        .map(scenario -> modelRouteResolver.resolve(scenario, request.userId()))
        .orElseThrow(this::routeNotConfigured);
  }

  private void assertDynamicEnabled(AiRunGovernanceRequest request) {
    AiPurposePolicy policy = policyResolver.resolve(request.purpose());
    if (!policy.enabled()) {
      throw rejected(AiGovernanceErrorCode.AI_PURPOSE_DISABLED);
    }
    EffectiveAiRuntimePolicy effective = runtimePolicyService.resolve(policy, request.userId());
    if (!effective.globalAiEnabled()) {
      throw rejected(AiGovernanceErrorCode.AI_GLOBALLY_DISABLED);
    }
    if (!effective.effectiveAiEnabled()) {
      AiGovernanceErrorCode code = effective.effectiveDisabledReason() == AiRuntimeDisabledReason.USER
          ? AiGovernanceErrorCode.AI_USER_DISABLED
          : AiGovernanceErrorCode.AI_PURPOSE_DISABLED;
      throw rejected(code);
    }
  }

  private Map<String, Object> metadata(
      AiRunGovernanceRequest request,
      Map<String, Object> admissionMetadata,
      ResolvedAiModelSnapshot snapshot
  ) {
    Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
    if (request.runId() != null) {
      metadata.put(AiGovernanceMetadataKeys.RUN_ID, request.runId());
    }
    metadata.put(AiGovernanceMetadataKeys.USER_ID, request.userId());
    metadata.put(AiGovernanceMetadataKeys.PURPOSE, request.purpose().name());
    metadata.put(AiGovernanceMetadataKeys.SOURCE, request.source().name());
    metadata.put(AiGovernanceMetadataKeys.QUOTA_SCOPE, request.quotaScope());
    request.source().businessScenario()
        .ifPresent(scenario -> metadata.put(AiGovernanceMetadataKeys.SCENARIO_CODE, scenario.code()));
    if (admissionMetadata != null) {
      metadata.putAll(admissionMetadata);
    }
    if (snapshot != null) {
      metadata.putAll(snapshot.trustedMetadata());
    }
    metadata.put(AiGovernanceMetadataKeys.CALL_KIND, request.callKind().name());
    return Map.copyOf(metadata);
  }

  private void bindInvocationTarget(String runId, LlmInvocationTarget target) {
    if (invocationTargetStore != null && runId != null && !runId.isBlank()) {
      invocationTargetStore.bind(runId, target);
    }
  }

  private void removeInvocationTarget(String runId) {
    if (invocationTargetStore != null && runId != null) {
      invocationTargetStore.remove(runId);
    }
  }

  public static AiGovernanceErrorCode errorCode(Throwable throwable) {
    if (throwable instanceof AiRunAdmissionException exception) {
      return exception.code();
    }
    if (throwable instanceof AiModelRouteException exception) {
      return exception.code();
    }
    if (!(throwable instanceof LlmException exception)) {
      return AiGovernanceErrorCode.AI_UNKNOWN;
    }
    return switch (exception.code()) {
      case TIMEOUT -> AiGovernanceErrorCode.AI_TIMEOUT;
      case RATE_LIMITED -> AiGovernanceErrorCode.AI_RATE_LIMITED;
      case PROVIDER_UNAVAILABLE -> AiGovernanceErrorCode.AI_PROVIDER_UNAVAILABLE;
      case CANCELLED -> AiGovernanceErrorCode.AI_CANCELLED;
      case RESPONSE_PARSE_FAILED -> AiGovernanceErrorCode.AI_STRUCTURED_OUTPUT_INVALID;
      default -> AiGovernanceErrorCode.AI_UNKNOWN;
    };
  }

  private AiModelRouteException routeNotConfigured() {
    return new AiModelRouteException(
        AiGovernanceErrorCode.AI_MODEL_ROUTE_NOT_CONFIGURED,
        "No AI model route is configured for this request.");
  }

  private static AiRunAdmissionException rejected(AiGovernanceErrorCode code) {
    HttpStatus status = switch (code) {
      case AI_GLOBALLY_DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
      case AI_USER_DISABLED, AI_PURPOSE_DISABLED -> HttpStatus.FORBIDDEN;
      default -> HttpStatus.BAD_REQUEST;
    };
    String message = switch (code) {
      case AI_GLOBALLY_DISABLED -> "AI service is globally disabled.";
      case AI_USER_DISABLED -> "AI usage is paused for this user.";
      default -> "AI completion is not enabled.";
    };
    return new AiRunAdmissionException(code, AiRunStatus.REJECTED_DISABLED, message, status, Map.of());
  }
}
