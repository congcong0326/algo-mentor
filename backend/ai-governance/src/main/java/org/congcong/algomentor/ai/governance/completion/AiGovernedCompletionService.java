package org.congcong.algomentor.ai.governance.completion;

import java.util.Map;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallKind;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.execution.AiRunGovernanceLease;
import org.congcong.algomentor.ai.governance.execution.AiRunGovernanceMode;
import org.congcong.algomentor.ai.governance.execution.AiRunGovernanceRequest;
import org.congcong.algomentor.ai.governance.execution.AiRunGovernanceService;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimePolicyService;
import org.congcong.algomentor.ai.governance.routing.AiModelRouteResolver;
import org.congcong.algomentor.ai.governance.routing.AiRunInvocationTargetStore;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;

/**
 * 为非 Agent step 的直接模型调用保留的兼容网关。
 *
 * <p>准入、路由和终态资源清理由 {@link AiRunGovernanceService} 统一协调；本类只负责
 * 直接调用的请求适配与 LLM 响应转换。</p>
 */
public class AiGovernedCompletionService implements AiCompletionGateway {

  private final LlmGateway delegate;
  private final AiRunGovernanceService governanceService;

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
    this(
        delegate,
        new AiRunGovernanceService(
            admissionService,
            lifecycleService,
            policyResolver,
            runtimePolicyService,
            modelRouteResolver,
            invocationTargetStore));
  }

  public AiGovernedCompletionService(LlmGateway delegate, AiRunGovernanceService governanceService) {
    this.delegate = delegate;
    this.governanceService = governanceService;
  }

  @Override
  public boolean isAllowed(AiCompletionContext context) {
    return governanceService.isAllowed(governanceRequest(context));
  }

  @Override
  public LlmCompletionResult complete(LlmCompletionRequest request, AiCompletionContext context) {
    AiRunGovernanceLease lease = governanceService.begin(governanceRequest(context));
    LlmCompletionRequest governedRequest = AiCompletionRequestEnricher.enrich(
        request.withInvocationTarget(lease.invocationTarget()),
        context,
        lease.metadata());
    try {
      LlmCompletionResult result = delegate.complete(governedRequest);
      lease.complete(
          toAiUsage(result),
          result.provider().value(),
          result.model().value());
      return result;
    } catch (RuntimeException exception) {
      lease.fail(AiRunGovernanceService.errorCode(exception), AiUsage.zero(), provider(exception), model(exception));
      throw exception;
    }
  }

  private static AiRunGovernanceRequest governanceRequest(AiCompletionContext context) {
    return new AiRunGovernanceRequest(
        mode(context.mode()),
        context.runId(),
        context.userId(),
        context.purpose(),
        context.source(),
        null,
        context.requestSize(),
        false,
        context.callKind(),
        context.quotaScope(),
        context.metadata());
  }

  private static AiRunGovernanceMode mode(AiCompletionMode mode) {
    return switch (mode) {
      case USER_ENTRY -> AiRunGovernanceMode.USER_ENTRY;
      case PARENT_RUN -> AiRunGovernanceMode.CHILD;
      case BACKGROUND -> AiRunGovernanceMode.BACKGROUND;
    };
  }

  private static AiUsage toAiUsage(LlmCompletionResult result) {
    return new AiUsage(
        result.usage().inputTokens(),
        result.usage().outputTokens(),
        result.usage().cachedTokens(),
        result.usage().reasoningTokens(),
        result.usage().totalTokens());
  }

  private static String provider(RuntimeException exception) {
    return exception instanceof org.congcong.algomentor.llm.core.exception.LlmException llmException
        && llmException.provider() != null ? llmException.provider().value() : null;
  }

  private static String model(RuntimeException exception) {
    return exception instanceof org.congcong.algomentor.llm.core.exception.LlmException llmException
        && llmException.model() != null ? llmException.model().value() : null;
  }
}
