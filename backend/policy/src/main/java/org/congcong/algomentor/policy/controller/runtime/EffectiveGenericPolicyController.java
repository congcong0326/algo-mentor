package org.congcong.algomentor.policy.controller.runtime;

import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.policy.controller.PolicyApiContractConstants;
import org.congcong.algomentor.policy.controller.PolicyAuthenticationSupport;
import org.congcong.algomentor.policy.controller.model.EffectiveGenericPolicyResponse;
import org.congcong.algomentor.policy.service.EffectiveGenericPolicyQueryService;
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

  public EffectiveGenericPolicyController(EffectiveGenericPolicyQueryService queryService) {
    this.queryService = queryService;
  }

  @GetMapping("/{typeCode}/effective")
  public ApiResponse<EffectiveGenericPolicyResponse> effective(
      @PathVariable String typeCode,
      Authentication authentication
  ) {
    return ApiResponse.success(queryService.resolveEffective(
            typeCode, PolicyAuthenticationSupport.currentUserId(authentication))
        .map(EffectiveGenericPolicyResponse::matched)
        .orElseGet(() -> EffectiveGenericPolicyResponse.unmatched(typeCode)));
  }
}
