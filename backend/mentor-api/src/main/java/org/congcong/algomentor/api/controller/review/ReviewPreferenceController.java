package org.congcong.algomentor.api.controller.review;

import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.review.model.MistakeReviewResponseMapper;
import org.congcong.algomentor.api.review.model.ReviewPreferenceRequest;
import org.congcong.algomentor.api.review.model.ReviewPreferenceResponse;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.review.ReviewPreferenceService;
import org.congcong.algomentor.mentor.application.review.ReviewPreferenceUpdate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewPreferenceController {

  private final ReviewPreferenceService preferenceService;
  private final CurrentUserIdProvider currentUserIdProvider;

  public ReviewPreferenceController(
      ReviewPreferenceService preferenceService,
      CurrentUserIdProvider currentUserIdProvider
  ) {
    this.preferenceService = preferenceService;
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @GetMapping(ApiContractConstants.ME_REVIEW_PREFERENCES_PATH)
  public ApiResponse<ReviewPreferenceResponse> get() {
    return ApiResponse.success(MistakeReviewResponseMapper.toPreferenceResponse(
        preferenceService.get(requireCurrentUserId())));
  }

  @PatchMapping(ApiContractConstants.ME_REVIEW_PREFERENCES_PATH)
  public ApiResponse<ReviewPreferenceResponse> update(@RequestBody ReviewPreferenceRequest request) {
    return ApiResponse.success(MistakeReviewResponseMapper.toPreferenceResponse(preferenceService.update(
        requireCurrentUserId(),
        new ReviewPreferenceUpdate(
            request == null ? null : request.desiredRetention(),
            request == null ? null : request.dailyNewLimit(),
            request == null ? null : request.dailyLearningLimit(),
            request == null ? null : request.dailyReviewLimit(),
            request == null ? null : request.aiSuggestionEnabled()))));
  }

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new MistakeReviewUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }
}
