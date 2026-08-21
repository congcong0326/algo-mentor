package org.congcong.algomentor.auth.controller.admin;

import org.congcong.algomentor.auth.controller.admin.model.AuthLoginSettingsResponse;
import org.congcong.algomentor.auth.controller.admin.model.AuthLoginSettingsUpdateRequest;
import org.congcong.algomentor.auth.loginsettings.service.AuthLoginSettingsService;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthLoginSettingsApiContractConstants.BASE_PATH)
@PreAuthorize("hasAuthority('auth-settings:manage')")
public class AuthLoginSettingsController {

  private final AuthLoginSettingsService service;

  public AuthLoginSettingsController(AuthLoginSettingsService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<AuthLoginSettingsResponse> get() {
    return ApiResponse.success(AuthLoginSettingsResponse.from(service.current()));
  }

  @PatchMapping
  public ApiResponse<AuthLoginSettingsResponse> update(
      @RequestBody(required = false) AuthLoginSettingsUpdateRequest request,
      Authentication authentication
  ) {
    return ApiResponse.success(AuthLoginSettingsResponse.from(service.update(
        request == null ? null : request.accountRegistrationEnabled(),
        request == null ? null : request.passwordLoginEnabled(),
        request == null ? null : request.passwordRegistrationEnabled(),
        request == null ? null : request.googleLoginEnabled(),
        request == null ? null : request.githubLoginEnabled(),
        operatorId(authentication))));
  }

  private static long operatorId(Authentication authentication) {
    if (authentication == null || authentication.getName() == null) {
      throw new IllegalArgumentException("无法解析管理员身份。");
    }
    try {
      return Long.parseLong(authentication.getName());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("无法解析管理员身份。", exception);
    }
  }
}
