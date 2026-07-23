package org.congcong.algomentor.policy.controller.admin;

import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.policy.controller.PolicyApiContractConstants;
import org.congcong.algomentor.policy.controller.PolicyAuthenticationSupport;
import org.congcong.algomentor.policy.controller.model.GenericPolicyPageResponse;
import org.congcong.algomentor.policy.controller.model.GenericPolicyResponse;
import org.congcong.algomentor.policy.controller.model.GenericPolicyUpdateRequest;
import org.congcong.algomentor.policy.controller.model.GenericPolicyWriteRequest;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;
import org.congcong.algomentor.policy.repository.GenericPolicySearchQuery;
import org.congcong.algomentor.policy.service.GenericPolicyCreateCommand;
import org.congcong.algomentor.policy.service.GenericPolicyManagementService;
import org.congcong.algomentor.policy.service.GenericPolicyUpdateCommand;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端通用策略 CRUD 与完整排序接口。 */
@RestController
@RequestMapping(PolicyApiContractConstants.ADMIN_POLICIES_BASE_PATH)
public final class AdminGenericPolicyController {

  private final GenericPolicyManagementService managementService;

  public AdminGenericPolicyController(GenericPolicyManagementService managementService) {
    this.managementService = managementService;
  }

  @GetMapping
  public ApiResponse<GenericPolicyPageResponse> search(
      @RequestParam String typeCode,
      @RequestParam(required = false) GenericPolicyStatus status,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize
  ) {
    return ApiResponse.success(GenericPolicyPageResponse.from(managementService.search(
        new GenericPolicySearchQuery(typeCode, status, keyword, page, pageSize))));
  }

  @PostMapping
  public ApiResponse<GenericPolicyResponse> create(
      @RequestBody GenericPolicyWriteRequest request,
      Authentication authentication
  ) {
    return ApiResponse.success(GenericPolicyResponse.from(managementService.create(
        new GenericPolicyCreateCommand(
            request.typeCode(),
            request.name(),
            request.description(),
            request.status(),
            request.subjectRange(),
            request.content(),
            PolicyAuthenticationSupport.currentUserId(authentication)))));
  }

  @GetMapping("/{policyId}")
  public ApiResponse<GenericPolicyResponse> get(@PathVariable long policyId) {
    return ApiResponse.success(GenericPolicyResponse.from(managementService.get(policyId)));
  }

  @PatchMapping("/{policyId}")
  public ApiResponse<GenericPolicyResponse> update(
      @PathVariable long policyId,
      @RequestBody GenericPolicyUpdateRequest request,
      Authentication authentication
  ) {
    return ApiResponse.success(GenericPolicyResponse.from(managementService.update(
        policyId,
        new GenericPolicyUpdateCommand(
            request.version(),
            request.name(),
            request.description(),
            request.status(),
            request.subjectRange(),
            request.content(),
            PolicyAuthenticationSupport.currentUserId(authentication)))));
  }

  @DeleteMapping("/{policyId}")
  public ApiResponse<Boolean> delete(
      @PathVariable long policyId,
      @RequestParam long version,
      Authentication authentication
  ) {
    return ApiResponse.success(managementService.delete(
        policyId, version, PolicyAuthenticationSupport.currentUserId(authentication)));
  }

}
