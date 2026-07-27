package org.congcong.algomentor.ai.governance.completion;

import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionException;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.model.AiActor;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
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
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.springframework.http.HttpStatus;

/** 为非 Agent step 的直接模型调用补齐准入、动态开关和调用关联 metadata。 */
public class AiGovernedCompletionService implements AiCompletionGateway {

  private final LlmGateway delegate;
  private final AiRunAdmissionService admissionService;
  private final AiRunLifecycleService lifecycleService;
  private final AiPurposePolicyResolver policyResolver;
  private final AiRuntimePolicyService runtimePolicyService;
  private final AiModelRouteResolver modelRouteResolver;
  private final AiRunInvocationTargetStore invocationTargetStore;

  public AiGovernedCompletionService(
      LlmGateway delegate,
      AiRunAdmissionService admissionService,
      AiRunLifecycleService lifecycleService,
      AiPurposePolicyResolver policyResolver,
      AiRuntimePolicyService runtimePolicyService,
      AiModelRouteResolver modelRouteResolver
  ) {
    this(
        delegate,
        admissionService,
        lifecycleService,
        policyResolver,
        runtimePolicyService,
        modelRouteResolver,
        null);
  }

  public AiGovernedCompletionService(
      LlmGateway delegate,
      AiRunAdmissionService admissionService,
      AiRunLifecycleService lifecycleService,
      AiPurposePolicyResolver policyResolver,
      AiRuntimePolicyService runtimePolicyService,
      AiModelRouteResolver modelRouteResolver,
      AiRunInvocationTargetStore invocationTargetStore
  ) {
    this.delegate = delegate;
    this.admissionService = admissionService;
    this.lifecycleService = lifecycleService;
    this.policyResolver = policyResolver;
    this.runtimePolicyService = runtimePolicyService;
    this.modelRouteResolver = modelRouteResolver;
    this.invocationTargetStore = invocationTargetStore;
  }

  @Override
  public boolean isAllowed(AiCompletionContext context) {
    try {
      assertDynamicEnabled(context);
      return true;
    } catch (AiRunAdmissionException exception) {
      return false;
    }
  }

  @Override
  public LlmCompletionResult complete(LlmCompletionRequest request, AiCompletionContext context) {
    return switch (context.mode()) {
      case USER_ENTRY -> completeUserEntry(request, context);
      case PARENT_RUN, BACKGROUND -> {
        assertDynamicEnabled(context);
        ResolvedAiModelSnapshot snapshot = resolveSnapshot(context);
        yield delegate.complete(enrich(request, context, null, snapshot));
      }
    };
  }

  private LlmCompletionResult completeUserEntry(
      LlmCompletionRequest request,
      AiCompletionContext context
  ) {
    assertDynamicEnabled(context);
    AiRunAdmission admission = admissionService.admit(new AiRunContext(
        context.runId(),
        new AiActor(context.userId(), Set.of(AuthRole.USER), true),
        context.purpose(),
        context.source(),
        null,
        context.requestSize(),
        false,
        context.metadata(),
        null));
    LlmCompletionRequest governedRequest = enrich(request, context, admission.metadata(), admissionTarget(admission));
    lifecycleService.markRunning(admission, null, null);
    try {
      LlmCompletionResult result = delegate.complete(governedRequest);
      lifecycleService.markCompleted(
          admission,
          toAiUsage(result),
          result.provider().value(),
          result.model().value());
      return result;
    } catch (RuntimeException exception) {
      lifecycleService.markFailed(
          admission,
          errorCode(exception),
          AiUsage.zero(),
          provider(exception),
          model(exception));
      throw exception;
    }
  }

  private ResolvedAiModelSnapshot resolveSnapshot(AiCompletionContext context) {
    if (modelRouteResolver == null) {
      throw new AiModelRouteException(
          AiGovernanceErrorCode.AI_MODEL_ROUTE_NOT_CONFIGURED,
          "No AI model route is configured for this request.");
    }
    return context.source().businessScenario()
        .map(scenario -> modelRouteResolver.resolve(scenario, context.userId()))
        .orElseThrow(() -> new AiModelRouteException(
            AiGovernanceErrorCode.AI_MODEL_ROUTE_NOT_CONFIGURED,
            "No AI model route is configured for this request."));
  }

  private LlmInvocationTarget admissionTarget(AiRunAdmission admission) {
    if (invocationTargetStore == null) {
      throw new AiModelRouteException(
          AiGovernanceErrorCode.AI_MODEL_ROUTE_NOT_CONFIGURED,
          "No AI model route is configured for this request.");
    }
    return invocationTargetStore.find(admission.runId()).orElseThrow(() -> new AiModelRouteException(
        AiGovernanceErrorCode.AI_MODEL_ROUTE_NOT_CONFIGURED,
        "No AI model route is configured for this request."));
  }

  private static LlmCompletionRequest enrich(
      LlmCompletionRequest request,
      AiCompletionContext context,
      Map<String, Object> admissionMetadata,
      ResolvedAiModelSnapshot snapshot
  ) {
    return enrich(
        request,
        context,
        mergeMetadata(admissionMetadata, snapshot.trustedMetadata()),
        snapshot.invocationTarget());
  }

  private static LlmCompletionRequest enrich(
      LlmCompletionRequest request,
      AiCompletionContext context,
      Map<String, Object> admissionMetadata,
      LlmInvocationTarget invocationTarget
  ) {
    LlmCompletionRequest targeted = request.withInvocationTarget(invocationTarget);
    return AiCompletionRequestEnricher.enrich(
        targeted,
        context,
        admissionMetadata);
  }

  private static Map<String, Object> mergeMetadata(
      Map<String, Object> first,
      Map<String, Object> second
  ) {
    java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>();
    if (first != null) {
      merged.putAll(first);
    }
    if (second != null) {
      merged.putAll(second);
    }
    return Map.copyOf(merged);
  }

  private void assertDynamicEnabled(AiCompletionContext context) {
    AiPurposePolicy policy = policyResolver.resolve(context.purpose());
    if (!policy.enabled()) {
      throw rejected(AiGovernanceErrorCode.AI_PURPOSE_DISABLED);
    }
    EffectiveAiRuntimePolicy effective = runtimePolicyService.resolve(policy, context.userId());
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

  private static AiUsage toAiUsage(LlmCompletionResult result) {
    return new AiUsage(
        result.usage().inputTokens(),
        result.usage().outputTokens(),
        result.usage().cachedTokens(),
        result.usage().reasoningTokens(),
        result.usage().totalTokens());
  }

  private static AiGovernanceErrorCode errorCode(RuntimeException exception) {
    if (!(exception instanceof LlmException llmException)) {
      return AiGovernanceErrorCode.AI_UNKNOWN;
    }
    return switch (llmException.code()) {
      case TIMEOUT -> AiGovernanceErrorCode.AI_TIMEOUT;
      case RATE_LIMITED -> AiGovernanceErrorCode.AI_RATE_LIMITED;
      case PROVIDER_UNAVAILABLE -> AiGovernanceErrorCode.AI_PROVIDER_UNAVAILABLE;
      case CANCELLED -> AiGovernanceErrorCode.AI_CANCELLED;
      case RESPONSE_PARSE_FAILED -> AiGovernanceErrorCode.AI_STRUCTURED_OUTPUT_INVALID;
      default -> AiGovernanceErrorCode.AI_UNKNOWN;
    };
  }

  private static String provider(RuntimeException exception) {
    return exception instanceof LlmException llmException && llmException.provider() != null
        ? llmException.provider().value()
        : null;
  }

  private static String model(RuntimeException exception) {
    return exception instanceof LlmException llmException && llmException.model() != null
        ? llmException.model().value()
        : null;
  }
}
