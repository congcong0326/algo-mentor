package org.congcong.algomentor.api.controller.ability;

import org.congcong.algomentor.api.ability.model.AbilityProfileResponse;
import org.congcong.algomentor.api.ability.service.AbilityProfileService;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AbilityProfileController {

  private final AbilityProfileService abilityProfileService;
  private final CurrentUserIdProvider currentUserIdProvider;

  public AbilityProfileController(
      AbilityProfileService abilityProfileService,
      CurrentUserIdProvider currentUserIdProvider
  ) {
    this.abilityProfileService = abilityProfileService;
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @GetMapping(ApiContractConstants.ABILITIES_PROFILE_PATH)
  public ApiResponse<AbilityProfileResponse> profile() {
    long userId = requireCurrentUserId();
    return ApiResponse.success(abilityProfileService.getProfile(userId));
  }

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new AbilityProfileUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }
}
