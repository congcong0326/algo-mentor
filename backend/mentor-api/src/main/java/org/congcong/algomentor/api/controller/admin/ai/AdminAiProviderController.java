package org.congcong.algomentor.api.controller.admin.ai;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import org.congcong.algomentor.ai.governance.provider.model.AiConfiguredModel;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;
import org.congcong.algomentor.ai.governance.provider.service.AiProviderManagementService;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiConfiguredModelResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiModelWriteRequest;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiProviderResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiProviderUpdateRequest;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiProviderWriteRequest;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 管理员维护代码注册 provider type 的实例与显式模型资源。 */
@RestController
@RequestMapping(AdminAiApiContractConstants.ADMIN_AI_BASE_PATH)
public class AdminAiProviderController {

  private final AiProviderManagementService managementService;

  public AdminAiProviderController(AiProviderManagementService managementService) {
    this.managementService = managementService;
  }

  @GetMapping(AdminAiApiContractConstants.PROVIDER_TYPES_PATH)
  public ApiResponse<ProviderTypeListResponse> providerTypes() {
    return ApiResponse.success(new ProviderTypeListResponse(managementService.supportedProviderTypes().stream()
        .map(type -> new ProviderTypeResponse(
            type.code(),
            type.displayName(),
            type.reasoningEfforts().stream().map(effort -> effort.wireValue()).toList(),
            type.defaultConfig()))
        .toList()));
  }

  @GetMapping(AdminAiApiContractConstants.PROVIDERS_PATH)
  public ApiResponse<ProviderListResponse> listProviders() {
    return ApiResponse.success(new ProviderListResponse(managementService.listProviders().stream()
        .map(provider -> providerResponse(provider, false))
        .toList()));
  }

  @PostMapping(AdminAiApiContractConstants.PROVIDERS_PATH)
  public org.springframework.http.ResponseEntity<ApiResponse<AdminAiProviderResponse>> createProvider(
      @RequestBody AdminAiProviderWriteRequest request,
      Authentication authentication
  ) {
    AdminAiProviderWriteRequest body = required(request);
    AdminAiRequestSupport.requireOperatorId(authentication);
    AiProviderInstance created = managementService.createProvider(
        body.name(), body.providerType(), requiredEnabled(body.enabled()), body.config());
    return org.springframework.http.ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(providerResponse(created, true)));
  }

  @GetMapping(AdminAiApiContractConstants.PROVIDER_ID_PATH)
  public ApiResponse<AdminAiProviderResponse> provider(@PathVariable long providerInstanceId) {
    return ApiResponse.success(providerResponse(managementService.getProvider(providerInstanceId), true));
  }

  @PutMapping(AdminAiApiContractConstants.PROVIDER_ID_PATH)
  public ApiResponse<AdminAiProviderResponse> updateProvider(
      @PathVariable long providerInstanceId,
      @RequestBody AdminAiProviderUpdateRequest request,
      Authentication authentication
  ) {
    AdminAiProviderUpdateRequest body = required(request);
    AdminAiRequestSupport.requireOperatorId(authentication);
    AiProviderInstance updated = managementService.updateProvider(
        providerInstanceId, body.name(), requiredEnabled(body.enabled()), body.config());
    return ApiResponse.success(providerResponse(updated, true));
  }

  @GetMapping(AdminAiApiContractConstants.PROVIDER_MODELS_PATH)
  public ApiResponse<ModelListResponse> listModels(@PathVariable long providerInstanceId) {
    return ApiResponse.success(new ModelListResponse(managementService.listModels(providerInstanceId).stream()
        .map(AdminAiProviderController::modelResponse)
        .toList()));
  }

  @PostMapping(AdminAiApiContractConstants.PROVIDER_MODELS_PATH)
  public org.springframework.http.ResponseEntity<ApiResponse<AdminAiConfiguredModelResponse>> createModel(
      @PathVariable long providerInstanceId,
      @RequestBody AdminAiModelWriteRequest request,
      Authentication authentication
  ) {
    AdminAiModelWriteRequest body = required(request);
    AdminAiRequestSupport.requireOperatorId(authentication);
    AiConfiguredModel created = managementService.createModel(
        providerInstanceId, body.displayName(), body.modelId(), requiredEnabled(body.enabled()));
    return org.springframework.http.ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(modelResponse(created)));
  }

  @GetMapping(AdminAiApiContractConstants.MODEL_ID_PATH)
  public ApiResponse<AdminAiConfiguredModelResponse> model(@PathVariable long modelId) {
    return ApiResponse.success(modelResponse(managementService.getModel(modelId)));
  }

  @PutMapping(AdminAiApiContractConstants.MODEL_ID_PATH)
  public ApiResponse<AdminAiConfiguredModelResponse> updateModel(
      @PathVariable long modelId,
      @RequestBody AdminAiModelWriteRequest request,
      Authentication authentication
  ) {
    AdminAiModelWriteRequest body = required(request);
    AdminAiRequestSupport.requireOperatorId(authentication);
    return ApiResponse.success(modelResponse(managementService.updateModel(
        modelId, body.displayName(), body.modelId(), requiredEnabled(body.enabled()))));
  }

  private AdminAiProviderResponse providerResponse(AiProviderInstance provider, boolean includeConfig) {
    int modelCount = managementService.listModels(provider.id()).size();
    return new AdminAiProviderResponse(
        provider.id(), provider.name(), provider.providerType(), provider.enabled(),
        provider.config().path("baseUrl").asText(null),
        includeConfig ? provider.config().deepCopy() : null,
        modelCount, provider.createdAt(), provider.updatedAt());
  }

  private static AdminAiConfiguredModelResponse modelResponse(AiConfiguredModel model) {
    return new AdminAiConfiguredModelResponse(
        model.id(), model.providerInstanceId(), model.displayName(), model.upstreamModelId(), model.enabled(),
        model.createdAt(), model.updatedAt());
  }

  private static boolean requiredEnabled(Boolean enabled) {
    if (enabled == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled is required");
    }
    return enabled;
  }

  private static <T> T required(T request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
    }
    return request;
  }

  public record ProviderTypeListResponse(List<ProviderTypeResponse> items) {
  }

  public record ProviderTypeResponse(
      String code,
      String displayName,
      List<String> reasoningEfforts,
      JsonNode defaultConfig
  ) {
    public ProviderTypeResponse {
      reasoningEfforts = reasoningEfforts == null ? List.of() : List.copyOf(reasoningEfforts);
      defaultConfig = defaultConfig == null
          ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
          : defaultConfig.deepCopy();
    }

    @Override
    public JsonNode defaultConfig() {
      return defaultConfig.deepCopy();
    }
  }

  public record ProviderListResponse(List<AdminAiProviderResponse> items) {
  }

  public record ModelListResponse(List<AdminAiConfiguredModelResponse> items) {
  }
}
