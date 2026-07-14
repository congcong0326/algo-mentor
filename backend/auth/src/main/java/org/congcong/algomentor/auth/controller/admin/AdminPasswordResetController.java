package org.congcong.algomentor.auth.controller.admin;

import org.congcong.algomentor.auth.controller.admin.model.AdminPasswordResetResponse;
import org.congcong.algomentor.auth.passwordreset.PasswordResetErrorCode;
import org.congcong.algomentor.auth.passwordreset.PasswordResetException;
import org.congcong.algomentor.auth.passwordreset.PasswordResetService;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AdminPasswordResetApiContractConstants.BASE_PATH)
public class AdminPasswordResetController {

  private final PasswordResetService passwordResetService;

  public AdminPasswordResetController(PasswordResetService passwordResetService) {
    this.passwordResetService = passwordResetService;
  }

  @PostMapping(AdminPasswordResetApiContractConstants.PASSWORD_RESET_PATH)
  public ApiResponse<AdminPasswordResetResponse> resetPassword(
      @PathVariable long userId,
      Authentication authentication
  ) {
    return ApiResponse.success(AdminPasswordResetResponse.from(
        passwordResetService.resetPassword(userId, operatorId(authentication))));
  }

  private static long operatorId(Authentication authentication) {
    if (authentication == null || authentication.getName() == null) {
      throw new PasswordResetException(PasswordResetErrorCode.USER_NOT_FOUND, "无法解析管理员身份。");
    }
    try {
      return Long.parseLong(authentication.getName());
    } catch (NumberFormatException exception) {
      throw new PasswordResetException(PasswordResetErrorCode.USER_NOT_FOUND, "无法解析管理员身份。");
    }
  }
}
