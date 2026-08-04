package org.congcong.algomentor.api.controller.admin.ai;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.ai.governance.provider.model.AiConfiguredModel;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;
import org.congcong.algomentor.ai.governance.provider.repository.AiConfiguredModelRepository;
import org.congcong.algomentor.ai.governance.provider.repository.AiProviderInstanceRepository;
import org.congcong.algomentor.ai.governance.routing.AiModelRoutePolicyContent;
import org.congcong.algomentor.ai.governance.routing.AiModelRoutePolicyTypeContributor;
import org.congcong.algomentor.ai.governance.routing.AiModelRoutingContract;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;
import org.congcong.algomentor.policy.model.ResolvedPolicy;
import org.congcong.algomentor.policy.repository.GenericPolicySearchQuery;
import org.congcong.algomentor.policy.service.GenericPolicyManagementService;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 代码注册场景的模型路由目录和纯读取命中模拟。 */
@RestController
@RequestMapping(AdminAiApiContractConstants.ADMIN_AI_BASE_PATH)
public class AdminAiModelRoutingController {

  private final AiModelRoutePolicyTypeContributor typeContributor;
  private final GenericPolicyQueryService policyQueryService;
  private final GenericPolicyManagementService policyManagementService;
  private final AiConfiguredModelRepository modelRepository;
  private final AiProviderInstanceRepository providerRepository;

  public AdminAiModelRoutingController(
      AiModelRoutePolicyTypeContributor typeContributor,
      GenericPolicyQueryService policyQueryService,
      GenericPolicyManagementService policyManagementService,
      AiConfiguredModelRepository modelRepository,
      AiProviderInstanceRepository providerRepository
  ) {
    this.typeContributor = typeContributor;
    this.policyQueryService = policyQueryService;
    this.policyManagementService = policyManagementService;
    this.modelRepository = modelRepository;
    this.providerRepository = providerRepository;
  }

  @GetMapping(AdminAiApiContractConstants.MODEL_ROUTING_SCENARIOS_PATH)
  public ApiResponse<ScenarioListResponse> scenarios() {
    return ApiResponse.success(new ScenarioListResponse(java.util.Arrays.stream(AiBusinessScenario.values())
        .map(this::scenario)
        .toList()));
  }

  @GetMapping(AdminAiApiContractConstants.MODEL_ROUTING_EFFECTIVE_PATH)
  public ApiResponse<EffectiveRouteResponse> effective(
      @PathVariable String scenarioCode,
      @RequestParam long userId
  ) {
    if (userId < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId must be positive");
    }
    AiBusinessScenario scenario = scenario(scenarioCode);
    Optional<ResolvedPolicy<AiModelRoutePolicyContent>> policy = policyQueryService.resolve(
        typeContributor.require(scenario), userId);
    if (policy.isEmpty()) {
      return ApiResponse.success(EffectiveRouteResponse.notMatched(scenario.code(), configured(scenario)));
    }
    ResolvedPolicy<AiModelRoutePolicyContent> matched = policy.get();
    return ApiResponse.success(EffectiveRouteResponse.matched(
        scenario.code(),
        matched.policyId(),
        matched.version(),
        matched.priority(),
        matched.matchSource().name(),
        matched.matchedSubjectId(),
        matched.content().reasoningEffort(),
        routeModel(matched.content().modelId())));
  }

  private ScenarioResponse scenario(AiBusinessScenario scenario) {
    String typeCode = AiModelRoutingContract.policyTypeCode(scenario);
    long enabled = policyManagementService.search(new GenericPolicySearchQuery(
        typeCode, GenericPolicyStatus.ENABLED, null, 1, 1)).total();
    long total = policyManagementService.search(new GenericPolicySearchQuery(
        typeCode, null, null, 1, 1)).total();
    return new ScenarioResponse(
        scenario.code(), scenario.categoryCode(), scenario.displayNameZh(), scenario.descriptionZh(),
        typeCode, enabled > 0, enabled, total);
  }

  private boolean configured(AiBusinessScenario scenario) {
    return policyManagementService.search(new GenericPolicySearchQuery(
        AiModelRoutingContract.policyTypeCode(scenario), GenericPolicyStatus.ENABLED, null, 1, 1)).total() > 0;
  }

  private RouteModelResponse routeModel(long modelId) {
    AiConfiguredModel model = modelRepository.findById(modelId).orElse(null);
    if (model == null) {
      return RouteModelResponse.missing(modelId);
    }
    AiProviderInstance provider = providerRepository.findById(model.providerInstanceId()).orElse(null);
    return new RouteModelResponse(
        model.id(), model.displayName(), model.upstreamModelId(), model.enabled(), model.providerInstanceId(),
        provider == null ? null : provider.name(),
        provider == null ? null : provider.providerType(),
        provider != null && provider.enabled(),
        provider == null || !provider.enabled() || !model.enabled() ? "AI_MODEL_UNAVAILABLE" : null);
  }

  private static AiBusinessScenario scenario(String code) {
    try {
      return AiBusinessScenario.fromCode(code);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown model routing scenario", exception);
    }
  }

  public record ScenarioListResponse(List<ScenarioResponse> items) {
  }

  public record ScenarioResponse(
      String scenarioCode,
      String categoryCode,
      String displayName,
      String description,
      String policyTypeCode,
      boolean configured,
      long enabledPolicyCount,
      long totalPolicyCount
  ) {
  }

  public record EffectiveRouteResponse(
      String scenarioCode,
      boolean configured,
      boolean matched,
      Long policyId,
      Long policyVersion,
      Integer priority,
      String matchSource,
      Long matchedSubjectId,
      LlmReasoningEffort reasoningEffort,
      String reason,
      RouteModelResponse model
  ) {
    static EffectiveRouteResponse notMatched(String scenarioCode, boolean configured) {
      return new EffectiveRouteResponse(
          scenarioCode, configured, false, null, null, null, null, null,
          null, AdminAiApiContractConstants.AI_MODEL_ROUTE_NOT_CONFIGURED, null);
    }

    static EffectiveRouteResponse matched(
        String scenarioCode,
        long policyId,
        long policyVersion,
        int priority,
        String matchSource,
        Long matchedSubjectId,
        LlmReasoningEffort reasoningEffort,
        RouteModelResponse model
    ) {
      return new EffectiveRouteResponse(
          scenarioCode, true, true, policyId, policyVersion, priority, matchSource, matchedSubjectId,
          reasoningEffort, model.reason(), model);
    }
  }

  public record RouteModelResponse(
      long id,
      String displayName,
      String modelId,
      boolean enabled,
      Long providerInstanceId,
      String providerInstanceName,
      String providerType,
      boolean providerEnabled,
      String reason
  ) {
    static RouteModelResponse missing(long id) {
      return new RouteModelResponse(id, null, null, false, null, null, null, false, "AI_MODEL_UNAVAILABLE");
    }
  }
}
