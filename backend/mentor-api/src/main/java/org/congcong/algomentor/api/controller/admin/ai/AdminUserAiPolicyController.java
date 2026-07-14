package org.congcong.algomentor.api.controller.admin.ai;

import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimeAdminService;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimePolicyService;
import org.congcong.algomentor.ai.governance.policy.runtime.AiUserPolicy;
import org.congcong.algomentor.ai.governance.policy.runtime.EffectiveAiRuntimePolicy;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminUserAiPolicyResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminUserAiPolicyUpdateRequest;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "spring.datasource.url")
@RequestMapping(AdminAiApiContractConstants.ADMIN_USERS_BASE_PATH)
public class AdminUserAiPolicyController {

  private final AiRuntimeAdminService adminService;
  private final AiRuntimePolicyService runtimePolicyService;
  private final AiPurposePolicyResolver policyResolver;
  private final AdminAiResponseMapper responseMapper;

  public AdminUserAiPolicyController(
      AiRuntimeAdminService adminService,
      AiRuntimePolicyService runtimePolicyService,
      AiPurposePolicyResolver policyResolver,
      IdentityUserRepository identityUserRepository
  ) {
    this.adminService = adminService;
    this.runtimePolicyService = runtimePolicyService;
    this.policyResolver = policyResolver;
    this.responseMapper = new AdminAiResponseMapper(identityUserRepository);
  }

  @GetMapping(AdminAiApiContractConstants.USER_AI_POLICY_PATH)
  public ApiResponse<AdminUserAiPolicyResponse> policy(@PathVariable long userId) {
    return ApiResponse.success(toResponse(userId, adminService.getUserPolicy(userId)));
  }

  @PatchMapping(AdminAiApiContractConstants.USER_AI_POLICY_PATH)
  public ApiResponse<AdminUserAiPolicyResponse> update(
      @PathVariable long userId,
      @RequestBody AdminUserAiPolicyUpdateRequest request,
      Authentication authentication
  ) {
    AiUserPolicy updated = adminService.updateUserPolicy(
        userId,
        request == null ? null : request.aiEnabledOverride(),
        request == null ? null : request.dailyRequestLimitOverride(),
        AdminAiRequestSupport.requireOperatorId(authentication));
    return ApiResponse.success(toResponse(userId, updated));
  }

  private AdminUserAiPolicyResponse toResponse(long userId, AiUserPolicy policy) {
    EffectiveAiRuntimePolicy effective = runtimePolicyService.resolve(
        policyResolver.resolve(AiPurpose.LEARNING_CHAT), userId);
    return new AdminUserAiPolicyResponse(
        userId,
        effective.globalAiEnabled(),
        policy.aiEnabledOverride(),
        effective.effectiveAiEnabled(),
        effective.effectiveDisabledReason() == null ? null : effective.effectiveDisabledReason().name(),
        effective.globalDefaultDailyRequestLimit(),
        policy.dailyRequestLimitOverride(),
        effective.effectiveDailyRequestLimit(),
        policy.updatedBy(),
        responseMapper.displayName(policy.updatedBy()),
        policy.updatedAt());
  }
}
