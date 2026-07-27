package org.congcong.algomentor.policy.controller.runtime;

import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.policy.controller.PolicyApiContractConstants;
import org.congcong.algomentor.policy.controller.PolicyAuthenticationSupport;
import org.congcong.algomentor.policy.controller.model.EffectiveGenericPolicyResponse;
import org.congcong.algomentor.policy.service.EffectiveGenericPolicyQueryService;
import org.congcong.algomentor.policy.service.GenericPolicyErrorCode;
import org.congcong.algomentor.policy.service.GenericPolicyException;
import org.congcong.algomentor.policy.type.GenericPolicyTypeExposure;
import org.congcong.algomentor.policy.type.GenericPolicyTypeRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 只查询当前认证用户的原始 JSON 有效策略接口。 */
@RestController
@RequestMapping("/api/policies")
public final class EffectiveGenericPolicyController {

  private final EffectiveGenericPolicyQueryService queryService;
  private final GenericPolicyTypeRegistry typeRegistry;

  public EffectiveGenericPolicyController(
      EffectiveGenericPolicyQueryService queryService,
      GenericPolicyTypeRegistry typeRegistry
  ) {
    this.queryService = queryService;
    this.typeRegistry = typeRegistry;
  }

  @GetMapping("/{typeCode}/effective")
  public ApiResponse<EffectiveGenericPolicyResponse> effective(
      @PathVariable String typeCode,
      Authentication authentication
  ) {
    if (typeRegistry.require(typeCode).exposure() == GenericPolicyTypeExposure.INTERNAL_ONLY) {
      throw new GenericPolicyException(
          GenericPolicyErrorCode.POLICY_ACCESS_DENIED,
          "该策略类型仅供内部运行时使用。");
    }
    return ApiResponse.success(queryService.resolveEffective(
            typeCode, PolicyAuthenticationSupport.currentUserId(authentication))
        .map(EffectiveGenericPolicyResponse::matched)
        .orElseGet(() -> EffectiveGenericPolicyResponse.unmatched(typeCode)));
  }
}
