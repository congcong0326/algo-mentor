package org.congcong.algomentor.api.controller.admin.ai;

import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimeAdminService;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimeSettings;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiSettingsResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiSettingsUpdateRequest;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "spring.datasource.url")
@RequestMapping(AdminAiApiContractConstants.ADMIN_AI_BASE_PATH)
public class AdminAiSettingsController {

  private final AiRuntimeAdminService service;
  private final AiPurposePolicyResolver policyResolver;
  private final AdminAiResponseMapper responseMapper;

  public AdminAiSettingsController(
      AiRuntimeAdminService service,
      AiPurposePolicyResolver policyResolver,
      IdentityUserRepository identityUserRepository
  ) {
    this.service = service;
    this.policyResolver = policyResolver;
    this.responseMapper = new AdminAiResponseMapper(identityUserRepository);
  }

  @GetMapping(AdminAiApiContractConstants.SETTINGS_PATH)
  public ApiResponse<AdminAiSettingsResponse> settings() {
    return ApiResponse.success(toResponse(service.getSettings(policyResolver.resolve(AiPurpose.LEARNING_CHAT))));
  }

  @PatchMapping(AdminAiApiContractConstants.SETTINGS_PATH)
  public ApiResponse<AdminAiSettingsResponse> update(
      @RequestBody AdminAiSettingsUpdateRequest request,
      Authentication authentication
  ) {
    long operatorUserId = AdminAiRequestSupport.requireOperatorId(authentication);
    AiRuntimeSettings updated = service.updateSettings(
        request == null ? null : request.aiEnabled(),
        request == null ? null : request.defaultDailyRequestLimit(),
        operatorUserId,
        policyResolver.resolve(AiPurpose.LEARNING_CHAT));
    return ApiResponse.success(toResponse(updated));
  }

  private AdminAiSettingsResponse toResponse(AiRuntimeSettings settings) {
    return new AdminAiSettingsResponse(
        settings.aiEnabled(),
        settings.defaultDailyRequestLimit(),
        settings.updatedBy(),
        responseMapper.displayName(settings.updatedBy()),
        settings.updatedAt());
  }
}
