package org.congcong.algomentor.auth.controller;

import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.model.AuthCapabilitiesResponse;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 向客户端暴露当前部署允许展示的认证入口。
 */
@RestController
@RequestMapping(AuthApiContractConstants.AUTH_API_BASE_PATH)
public class AuthCapabilitiesController {

  private final AuthProperties properties;

  public AuthCapabilitiesController(AuthProperties properties) {
    this.properties = properties;
  }

  @GetMapping(AuthApiContractConstants.CAPABILITIES_PATH)
  public ApiResponse<AuthCapabilitiesResponse> capabilities() {
    boolean passwordLoginEnabled = properties.isPasswordLoginEnabled();
    return ApiResponse.success(new AuthCapabilitiesResponse(
        passwordLoginEnabled,
        passwordLoginEnabled && properties.isPasswordRegistrationEnabled()));
  }
}
