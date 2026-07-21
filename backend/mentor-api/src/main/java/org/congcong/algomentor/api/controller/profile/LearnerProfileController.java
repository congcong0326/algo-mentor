package org.congcong.algomentor.api.controller.profile;

import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.profile.model.LearnerProfileResponse;
import org.congcong.algomentor.api.profile.service.LearnerProfileViewService;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LearnerProfileController {

  private final LearnerProfileViewService profileService;
  private final CurrentUserIdProvider currentUserIdProvider;

  public LearnerProfileController(
      LearnerProfileViewService profileService,
      CurrentUserIdProvider currentUserIdProvider
  ) {
    this.profileService = profileService;
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @GetMapping(ApiContractConstants.ME_LEARNER_PROFILE_PATH)
  public ApiResponse<LearnerProfileResponse> profile() {
    return ApiResponse.success(profileService.getProfile(requireCurrentUserId()));
  }

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new LearnerProfileUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }
}
