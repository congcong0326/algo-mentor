package org.congcong.algomentor.api.controller.review;

import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.review.model.MistakeReviewResponseMapper;
import org.congcong.algomentor.api.review.model.ReviewQueueResponse;
import org.congcong.algomentor.api.review.model.ReviewSummaryResponse;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.review.MistakeReviewException;
import org.congcong.algomentor.mentor.application.review.ReviewSessionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewSessionController {

  private final ObjectProvider<ReviewSessionService> reviewSessionService;
  private final CurrentUserIdProvider currentUserIdProvider;

  public ReviewSessionController(
      ObjectProvider<ReviewSessionService> reviewSessionService,
      CurrentUserIdProvider currentUserIdProvider
  ) {
    this.reviewSessionService = reviewSessionService;
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @GetMapping(ApiContractConstants.REVIEW_SESSIONS_BASE_PATH + "/queue")
  public ApiResponse<ReviewQueueResponse> queue(@RequestParam(defaultValue = "20") int limit) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(MistakeReviewResponseMapper.toQueueResponse(
        requiredReviewSessionService().dueQueue(userId, limit)));
  }

  @GetMapping(ApiContractConstants.REVIEW_SESSIONS_BASE_PATH + "/summary")
  public ApiResponse<ReviewSummaryResponse> summary() {
    long userId = requireCurrentUserId();
    return ApiResponse.success(MistakeReviewResponseMapper.toSummaryResponse(
        requiredReviewSessionService().summary(userId)));
  }

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new MistakeReviewUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }

  private ReviewSessionService requiredReviewSessionService() {
    return reviewSessionService.getIfAvailable(() -> {
      throw new MistakeReviewException("REVIEW_SESSION_SERVICE_UNAVAILABLE", "复习会话服务不可用。");
    });
  }
}
