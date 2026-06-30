package org.congcong.algomentor.identity.controller;

import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.identity.controller.model.AdminUserDetailResponse;
import org.congcong.algomentor.identity.controller.model.AdminUserListQuery;
import org.congcong.algomentor.identity.controller.model.AdminUserPageResponse;
import org.congcong.algomentor.identity.controller.model.AdminUserResponseMapper;
import org.congcong.algomentor.identity.controller.model.AdminUserStatusUpdateRequest;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.congcong.algomentor.identity.service.IdentityUserErrorCode;
import org.congcong.algomentor.identity.service.IdentityUserManagementException;
import org.congcong.algomentor.identity.service.IdentityUserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AdminUserApiContractConstants.ADMIN_USERS_BASE_PATH)
public class AdminUserController {

  private final IdentityUserService service;
  private final AdminUserResponseMapper responseMapper;

  public AdminUserController(IdentityUserService service, IdentityUserRepository repository) {
    this.service = service;
    this.responseMapper = new AdminUserResponseMapper(repository);
  }

  @GetMapping
  public ApiResponse<AdminUserPageResponse> listUsers(@ModelAttribute AdminUserListQuery query) {
    return ApiResponse.success(responseMapper.toPageResponse(service.searchUsers(query.toSearchQuery())));
  }

  @GetMapping(AdminUserApiContractConstants.USER_ID_PATH)
  public ApiResponse<AdminUserDetailResponse> detail(@PathVariable long userId) {
    return ApiResponse.success(responseMapper.toDetailResponse(service.getUser(userId)));
  }

  @PatchMapping(AdminUserApiContractConstants.STATUS_PATH)
  public ApiResponse<AdminUserDetailResponse> updateStatus(
      @PathVariable long userId,
      @RequestBody AdminUserStatusUpdateRequest request,
      Authentication authentication
  ) {
    long operatorId = requireOperatorId(authentication);
    AuthUser updated = service.updateStatus(userId, operatorId, request.status());
    return ApiResponse.success(responseMapper.toDetailResponse(updated));
  }

  @DeleteMapping(AdminUserApiContractConstants.USER_ID_PATH)
  public ApiResponse<AdminUserDetailResponse> delete(@PathVariable long userId, Authentication authentication) {
    long operatorId = requireOperatorId(authentication);
    return ApiResponse.success(responseMapper.toDetailResponse(service.softDelete(userId, operatorId)));
  }

  private long requireOperatorId(Authentication authentication) {
    if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
      throw new IdentityUserManagementException(
          IdentityUserErrorCode.USER_NOT_FOUND,
          "当前请求未登录或无法解析当前用户。");
    }
    try {
      return Long.parseLong(authentication.getName());
    } catch (NumberFormatException exception) {
      throw new IdentityUserManagementException(
          IdentityUserErrorCode.USER_NOT_FOUND,
          "当前请求未登录或无法解析当前用户。");
    }
  }
}
