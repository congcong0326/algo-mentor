package org.congcong.algomentor.policy.controller;

import org.congcong.algomentor.policy.service.GenericPolicyErrorCode;
import org.congcong.algomentor.policy.service.GenericPolicyException;
import org.springframework.security.core.Authentication;

/** 从认证上下文提取稳定的本地用户 ID。 */
public final class PolicyAuthenticationSupport {

  private PolicyAuthenticationSupport() {
  }

  public static long currentUserId(Authentication authentication) {
    if (authentication == null || authentication.getName() == null) {
      throw invalidAuthentication();
    }
    try {
      long userId = Long.parseLong(authentication.getName());
      if (userId < 1) {
        throw invalidAuthentication();
      }
      return userId;
    } catch (NumberFormatException exception) {
      throw invalidAuthentication();
    }
  }

  private static GenericPolicyException invalidAuthentication() {
    return new GenericPolicyException(
        GenericPolicyErrorCode.POLICY_INVALID_REQUEST,
        "无法解析当前认证用户。");
  }
}
