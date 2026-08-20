package org.congcong.algomentor.api.controller.review;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.review.model.ArchiveReviewCardRequest;
import org.congcong.algomentor.api.review.model.CreateReviewCardRequest;
import org.congcong.algomentor.api.review.model.ReviewAttemptResponse;
import org.congcong.algomentor.api.review.model.ReviewCardContextResponse;
import org.congcong.algomentor.api.review.model.ReviewCardOverviewPageResponse;
import org.congcong.algomentor.api.review.model.ReviewCardResponse;
import org.congcong.algomentor.api.review.model.ReviewResponseMapper;
import org.congcong.algomentor.api.review.model.SubmitReviewAttemptRequest;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiErrorLocales;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.review.ReviewException;
import org.congcong.algomentor.mentor.application.review.attempt.ReviewAttemptService;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardService;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardOverviewService;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardSource;
import org.congcong.algomentor.mentor.application.review.card.ReviewQueueService;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewCardController {

  private final ObjectProvider<ReviewCardService> cardService;
  private final ObjectProvider<ReviewCardOverviewService> cardOverviewService;
  private final ObjectProvider<ReviewQueueService> queueService;
  private final ObjectProvider<ReviewAttemptService> attemptService;
  private final CurrentUserIdProvider currentUserIdProvider;

  public ReviewCardController(
      ObjectProvider<ReviewCardService> cardService,
      ObjectProvider<ReviewCardOverviewService> cardOverviewService,
      ObjectProvider<ReviewQueueService> queueService,
      ObjectProvider<ReviewAttemptService> attemptService,
      CurrentUserIdProvider currentUserIdProvider
  ) {
    this.cardService = cardService;
    this.cardOverviewService = cardOverviewService;
    this.queueService = queueService;
    this.attemptService = attemptService;
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @GetMapping(ApiContractConstants.REVIEW_CARDS_BASE_PATH)
  public ApiResponse<ReviewCardOverviewPageResponse> list(
      @RequestParam(required = false) String source,
      @RequestParam(defaultValue = "false") boolean mistakeOnly,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page
  ) {
    return ApiResponse.success(ReviewResponseMapper.toCardOverviewPageResponse(requiredCardOverviewService().list(
        requireCurrentUserId(),
        parseSource(source),
        mistakeOnly,
        keyword,
        page)));
  }

  @PostMapping(ApiContractConstants.REVIEW_CARDS_BASE_PATH)
  public ApiResponse<ReviewCardResponse> create(@Valid @RequestBody CreateReviewCardRequest request) {
    return ApiResponse.success(ReviewResponseMapper.toCardResponse(
        requiredCardService().mark(requireCurrentUserId(), request.problemSlug())));
  }

  @PatchMapping(ApiContractConstants.REVIEW_CARDS_BASE_PATH + "/{cardId}/archive")
  public ApiResponse<ReviewCardResponse> archive(
      @PathVariable long cardId,
      @RequestBody ArchiveReviewCardRequest request
  ) {
    return ApiResponse.success(ReviewResponseMapper.toCardResponse(
        requiredCardService().archive(requireCurrentUserId(), cardId, request.archived())));
  }

  @GetMapping(ApiContractConstants.REVIEW_CARDS_BASE_PATH + "/{cardId}/context")
  public ApiResponse<ReviewCardContextResponse> context(
      @PathVariable long cardId,
      @RequestHeader(name = ApiContractConstants.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage,
      @RequestParam(required = false) String timezone,
      HttpServletResponse response
  ) {
    response.addHeader(HttpHeaders.VARY, ApiContractConstants.ACCEPT_LANGUAGE_HEADER);
    return ApiResponse.success(ReviewResponseMapper.toContextResponse(
        requiredQueueService().context(
            requireCurrentUserId(),
            cardId,
            ApiErrorLocales.parse(acceptLanguage).toLanguageTag(),
            ReviewZoneId.parse(timezone))));
  }

  @PostMapping(ApiContractConstants.REVIEW_CARDS_BASE_PATH + "/{cardId}/attempts")
  public ApiResponse<ReviewAttemptResponse> submitAttempt(
      @PathVariable long cardId,
      @RequestParam(required = false) String timezone,
      @Valid @RequestBody SubmitReviewAttemptRequest request
  ) {
    return ApiResponse.success(ReviewResponseMapper.toAttemptResponse(requiredAttemptService().submit(
        requireCurrentUserId(),
        cardId,
        request.clientAttemptId(),
        ReviewRating.parse(request.rating()),
        ReviewZoneId.parse(timezone))));
  }

  @GetMapping(ApiContractConstants.REVIEW_CARDS_BASE_PATH + "/{cardId}/attempts")
  public ApiResponse<List<ReviewAttemptResponse>> attempts(
      @PathVariable long cardId,
      @RequestParam(defaultValue = "20") int limit
  ) {
    return ApiResponse.success(ReviewResponseMapper.toAttemptResponses(
        requiredAttemptService().history(requireCurrentUserId(), cardId, limit)));
  }

  private ReviewCardSource parseSource(String source) {
    if (source == null || source.isBlank()) {
      return null;
    }
    try {
      return ReviewCardSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ReviewException("REVIEW_CARD_INVALID_FILTER", "复习卡来源筛选参数不合法。");
    }
  }

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new ReviewUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }

  private ReviewCardService requiredCardService() {
    return cardService.getIfAvailable(() -> {
      throw new ReviewException("REVIEW_CARD_SERVICE_UNAVAILABLE", "复习卡服务不可用。");
    });
  }

  private ReviewCardOverviewService requiredCardOverviewService() {
    return cardOverviewService.getIfAvailable(() -> {
      throw new ReviewException("REVIEW_CARD_OVERVIEW_SERVICE_UNAVAILABLE", "复习卡列表服务不可用。");
    });
  }

  private ReviewQueueService requiredQueueService() {
    return queueService.getIfAvailable(() -> {
      throw new ReviewException("REVIEW_QUEUE_SERVICE_UNAVAILABLE", "复习队列服务不可用。");
    });
  }

  private ReviewAttemptService requiredAttemptService() {
    return attemptService.getIfAvailable(() -> {
      throw new ReviewException("REVIEW_ATTEMPT_SERVICE_UNAVAILABLE", "复习提交服务不可用。");
    });
  }
}
