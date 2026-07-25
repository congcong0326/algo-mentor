package org.congcong.algomentor.api.controller.review;

import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.review.model.ReviewQueueResponse;
import org.congcong.algomentor.api.review.model.ReviewResponseMapper;
import org.congcong.algomentor.api.review.model.ReviewSummaryResponse;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.review.ReviewException;
import org.congcong.algomentor.mentor.application.review.card.ReviewQueueService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewSessionController {

  private final ObjectProvider<ReviewQueueService> queueService;
  private final CurrentUserIdProvider currentUserIdProvider;

  public ReviewSessionController(
      ObjectProvider<ReviewQueueService> queueService,
      CurrentUserIdProvider currentUserIdProvider
  ) {
    this.queueService = queueService;
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @GetMapping(ApiContractConstants.REVIEW_SESSIONS_BASE_PATH + "/queue")
  public ApiResponse<ReviewQueueResponse> queue(@RequestParam(defaultValue = "20") int limit) {
    return ApiResponse.success(ReviewResponseMapper.toQueueResponse(
        requiredQueueService().dueQueue(requireCurrentUserId(), limit)));
  }

  @GetMapping(ApiContractConstants.REVIEW_SESSIONS_BASE_PATH + "/summary")
  public ApiResponse<ReviewSummaryResponse> summary(
      @RequestParam(required = false) String timezone
  ) {
    return ApiResponse.success(ReviewResponseMapper.toSummaryResponse(
        requiredQueueService().summary(requireCurrentUserId(), timezone)));
  }

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new ReviewUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }

  private ReviewQueueService requiredQueueService() {
    return queueService.getIfAvailable(() -> {
      throw new ReviewException("REVIEW_QUEUE_SERVICE_UNAVAILABLE", "复习队列服务不可用。");
    });
  }
}
