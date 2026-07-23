package org.congcong.algomentor.identity.controller.group;

public final class AdminUserGroupApiContractConstants {

  public static final String BASE_PATH = "/api/admin/user-groups";
  public static final String GROUP_ID_PATH = "/{groupId}";
  public static final String MEMBERS_PATH = "/{groupId}/members";
  public static final String MEMBER_PATH = "/{groupId}/members/{userId}";

  public static final String REQUEST_BODY_INVALID = "REQUEST_BODY_INVALID";
  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

  private AdminUserGroupApiContractConstants() {
  }
}
