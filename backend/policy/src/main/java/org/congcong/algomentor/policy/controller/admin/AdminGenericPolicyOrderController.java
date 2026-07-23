package org.congcong.algomentor.policy.controller.admin;

import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.policy.controller.PolicyAuthenticationSupport;
import org.congcong.algomentor.policy.controller.model.GenericPolicyOrderRequest;
import org.congcong.algomentor.policy.service.GenericPolicyManagementService;
import org.congcong.algomentor.policy.service.GenericPolicyOrderCommand;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 完整顺序请求独立于策略 CRUD 路径，避免逐条 priority 更新。 */
@RestController
@RequestMapping("/api/admin/policy-types")
public final class AdminGenericPolicyOrderController {

  private final GenericPolicyManagementService managementService;

  public AdminGenericPolicyOrderController(GenericPolicyManagementService managementService) {
    this.managementService = managementService;
  }

  @PutMapping("/{typeCode}/order")
  public ApiResponse<Void> reorder(
      @PathVariable String typeCode,
      @RequestBody GenericPolicyOrderRequest request,
      Authentication authentication
  ) {
    managementService.reorder(
        typeCode,
        new GenericPolicyOrderCommand(
            request.policyIds(), request.versions(), PolicyAuthenticationSupport.currentUserId(authentication)));
    return ApiResponse.success(null);
  }
}
