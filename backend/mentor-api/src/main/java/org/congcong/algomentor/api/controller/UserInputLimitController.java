package org.congcong.algomentor.api.controller;

import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.config.UserInputLimitProperties;
import org.congcong.algomentor.api.input.UserInputLimitsResponse;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 对前端公开当前生效的用户输入数值上限。 */
@RestController
public class UserInputLimitController {

  private final UserInputLimitProperties properties;

  public UserInputLimitController(UserInputLimitProperties properties) {
    this.properties = properties;
  }

  @GetMapping(ApiContractConstants.USER_INPUT_LIMITS_PATH)
  public ApiResponse<UserInputLimitsResponse> limits() {
    return ApiResponse.success(UserInputLimitsResponse.from(properties));
  }
}
