package org.congcong.algomentor.identity.controller.group;

import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.identity.controller.group.model.AdminUserGroupCreateRequest;
import org.congcong.algomentor.identity.controller.group.model.AdminUserGroupListQuery;
import org.congcong.algomentor.identity.controller.group.model.AdminUserGroupMemberAddRequest;
import org.congcong.algomentor.identity.controller.group.model.AdminUserGroupMemberListQuery;
import org.congcong.algomentor.identity.controller.group.model.AdminUserGroupMemberPageResponse;
import org.congcong.algomentor.identity.controller.group.model.AdminUserGroupPageResponse;
import org.congcong.algomentor.identity.controller.group.model.AdminUserGroupResponse;
import org.congcong.algomentor.identity.controller.group.model.AdminUserGroupUpdateRequest;
import org.congcong.algomentor.identity.group.service.UserGroupDeleteResult;
import org.congcong.algomentor.identity.group.service.UserGroupErrorCode;
import org.congcong.algomentor.identity.group.service.UserGroupManagementException;
import org.congcong.algomentor.identity.group.service.UserGroupMemberBatchResult;
import org.congcong.algomentor.identity.group.service.UserGroupMemberRemovalResult;
import org.congcong.algomentor.identity.group.service.UserGroupService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AdminUserGroupApiContractConstants.BASE_PATH)
public class AdminUserGroupController {

  private final UserGroupService service;

  public AdminUserGroupController(UserGroupService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<AdminUserGroupPageResponse> list(@ModelAttribute AdminUserGroupListQuery query) {
    return ApiResponse.success(AdminUserGroupPageResponse.from(service.searchGroups(query.toSearchQuery())));
  }

  @PostMapping
  public ApiResponse<AdminUserGroupResponse> create(
      @RequestBody AdminUserGroupCreateRequest request,
      Authentication authentication
  ) {
    return ApiResponse.success(AdminUserGroupResponse.from(service.createGroup(
        request.code(),
        request.name(),
        request.description(),
        requireOperatorId(authentication))));
  }

  @GetMapping(AdminUserGroupApiContractConstants.GROUP_ID_PATH)
  public ApiResponse<AdminUserGroupResponse> detail(@PathVariable long groupId) {
    return ApiResponse.success(AdminUserGroupResponse.from(service.getGroup(groupId)));
  }

  @PatchMapping(AdminUserGroupApiContractConstants.GROUP_ID_PATH)
  public ApiResponse<AdminUserGroupResponse> update(
      @PathVariable long groupId,
      @RequestBody AdminUserGroupUpdateRequest request,
      Authentication authentication
  ) {
    return ApiResponse.success(AdminUserGroupResponse.from(service.updateGroup(
        groupId,
        request.name(),
        request.description(),
        request.status(),
        requireOperatorId(authentication))));
  }

  @DeleteMapping(AdminUserGroupApiContractConstants.GROUP_ID_PATH)
  public ApiResponse<UserGroupDeleteResult> delete(
      @PathVariable long groupId,
      Authentication authentication
  ) {
    return ApiResponse.success(service.deleteGroup(groupId, requireOperatorId(authentication)));
  }

  @GetMapping(AdminUserGroupApiContractConstants.MEMBERS_PATH)
  public ApiResponse<AdminUserGroupMemberPageResponse> members(
      @PathVariable long groupId,
      @ModelAttribute AdminUserGroupMemberListQuery query
  ) {
    return ApiResponse.success(AdminUserGroupMemberPageResponse.from(
        service.searchMembers(groupId, query.toSearchQuery())));
  }

  @PostMapping(AdminUserGroupApiContractConstants.MEMBERS_PATH)
  public ApiResponse<UserGroupMemberBatchResult> addMembers(
      @PathVariable long groupId,
      @RequestBody AdminUserGroupMemberAddRequest request,
      Authentication authentication
  ) {
    return ApiResponse.success(service.addMembers(
        groupId,
        request.userIds(),
        request.expiresAt(),
        requireOperatorId(authentication)));
  }

  @DeleteMapping(AdminUserGroupApiContractConstants.MEMBER_PATH)
  public ApiResponse<UserGroupMemberRemovalResult> removeMember(
      @PathVariable long groupId,
      @PathVariable long userId,
      Authentication authentication
  ) {
    return ApiResponse.success(service.removeMember(
        groupId,
        userId,
        requireOperatorId(authentication)));
  }

  private long requireOperatorId(Authentication authentication) {
    if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
      throw new UserGroupManagementException(
          UserGroupErrorCode.USER_GROUP_INVALID_REQUEST,
          "当前请求未登录或无法解析当前用户。");
    }
    try {
      return Long.parseLong(authentication.getName());
    } catch (NumberFormatException exception) {
      throw new UserGroupManagementException(
          UserGroupErrorCode.USER_GROUP_INVALID_REQUEST,
          "当前请求未登录或无法解析当前用户。");
    }
  }
}
