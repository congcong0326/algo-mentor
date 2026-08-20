package org.congcong.algomentor.api.controller.activity;

import org.congcong.algomentor.api.activity.model.ActivityContributionResponse;
import org.congcong.algomentor.api.activity.service.ActivityContributionService;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ActivityContributionController {

  private final ActivityContributionService activityContributionService;
  private final CurrentUserIdProvider currentUserIdProvider;

  public ActivityContributionController(
      ActivityContributionService activityContributionService,
      CurrentUserIdProvider currentUserIdProvider
  ) {
    this.activityContributionService = activityContributionService;
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @GetMapping(ApiContractConstants.ACTIVITY_CONTRIBUTIONS_PATH)
  public ApiResponse<ActivityContributionResponse> contributions(
      @RequestParam(name = ApiContractConstants.ACTIVITY_TIMEZONE_PARAM, required = false) String timezone
  ) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(activityContributionService.getContributions(userId, timezone));
  }

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new ActivityContributionUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }
}
