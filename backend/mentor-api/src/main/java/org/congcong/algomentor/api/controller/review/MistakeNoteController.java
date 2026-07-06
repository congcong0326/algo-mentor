package org.congcong.algomentor.api.controller.review;

import jakarta.validation.Valid;
import java.util.List;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.review.model.ArchiveMistakeRequest;
import org.congcong.algomentor.api.review.model.ConfirmRecallRequest;
import org.congcong.algomentor.api.review.model.MarkMistakeRequest;
import org.congcong.algomentor.api.review.model.MistakeNoteResponse;
import org.congcong.algomentor.api.review.model.MistakeReviewResponseMapper;
import org.congcong.algomentor.api.review.model.RecallReviewResponse;
import org.congcong.algomentor.api.review.model.RecallEvaluationResponse;
import org.congcong.algomentor.api.review.model.RecallConfirmResponse;
import org.congcong.algomentor.api.review.model.RateRecallRequest;
import org.congcong.algomentor.api.review.model.ReviewCardResponse;
import org.congcong.algomentor.api.review.model.ReviewIntervalPreviewResponse;
import org.congcong.algomentor.api.review.model.ReviewProblemStatementResponse;
import org.congcong.algomentor.api.review.model.ReviewProblemStatementResponseMapper;
import org.congcong.algomentor.api.review.model.SubmitRecallRequest;
import org.congcong.algomentor.api.review.model.UpdateMistakeNoteRequest;
import org.congcong.algomentor.api.review.service.MistakeNoteDisplayInfoResolver;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiErrorLocales;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.review.MistakeNoteService;
import org.congcong.algomentor.mentor.application.review.MistakeReviewException;
import org.congcong.algomentor.mentor.application.review.MistakeSource;
import org.congcong.algomentor.mentor.application.review.ReviewProblemCatalog;
import org.congcong.algomentor.mentor.application.review.ReviewRating;
import org.congcong.algomentor.mentor.application.review.ReviewSessionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MistakeNoteController {

  private final ObjectProvider<MistakeNoteService> mistakeNoteService;
  private final ObjectProvider<ReviewSessionService> reviewSessionService;
  private final ObjectProvider<ReviewProblemCatalog> reviewProblemCatalog;
  private final MistakeNoteDisplayInfoResolver displayInfoResolver;
  private final CurrentUserIdProvider currentUserIdProvider;

  public MistakeNoteController(
      ObjectProvider<MistakeNoteService> mistakeNoteService,
      ObjectProvider<ReviewSessionService> reviewSessionService,
      ObjectProvider<ReviewProblemCatalog> reviewProblemCatalog,
      MistakeNoteDisplayInfoResolver displayInfoResolver,
      CurrentUserIdProvider currentUserIdProvider
  ) {
    this.mistakeNoteService = mistakeNoteService;
    this.reviewSessionService = reviewSessionService;
    this.reviewProblemCatalog = reviewProblemCatalog;
    this.displayInfoResolver = displayInfoResolver;
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @GetMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH)
  public ApiResponse<List<MistakeNoteResponse>> list(
      @RequestParam(required = false) String source,
      @RequestParam(defaultValue = "false") boolean mistakeOnly,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset,
      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
  ) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(MistakeReviewResponseMapper.toNoteResponses(
        requiredMistakeNoteService().list(
            userId,
            parseSource(source),
            mistakeOnly,
            keyword,
            limit,
            offset),
        displayInfoResolver,
        ApiErrorLocales.parse(acceptLanguage)));
  }

  @PostMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH)
  public ApiResponse<MistakeNoteResponse> mark(
      @Valid @RequestBody MarkMistakeRequest request,
      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
  ) {
    long userId = requireCurrentUserId();
    var note = requiredMistakeNoteService().mark(userId, request.problemSlug());
    return ApiResponse.success(MistakeReviewResponseMapper.toNoteResponse(
        note,
        displayInfoResolver.resolve(note, ApiErrorLocales.parse(acceptLanguage))));
  }

  @PatchMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH + "/{noteId}/archive")
  public ApiResponse<MistakeNoteResponse> archive(
      @PathVariable long noteId,
      @RequestBody ArchiveMistakeRequest request,
      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
  ) {
    long userId = requireCurrentUserId();
    var note = requiredMistakeNoteService().archive(userId, noteId, request.archived());
    return ApiResponse.success(MistakeReviewResponseMapper.toNoteResponse(
        note,
        displayInfoResolver.resolve(note, ApiErrorLocales.parse(acceptLanguage))));
  }

  @PatchMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH + "/{noteId}/note")
  public ApiResponse<MistakeNoteResponse> updateNote(
      @PathVariable long noteId,
      @RequestBody UpdateMistakeNoteRequest request,
      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
  ) {
    long userId = requireCurrentUserId();
    var note = requiredMistakeNoteService().updatePersistentNote(userId, noteId, request.text());
    return ApiResponse.success(MistakeReviewResponseMapper.toNoteResponse(
        note,
        displayInfoResolver.resolve(note, ApiErrorLocales.parse(acceptLanguage))));
  }

  @GetMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH + "/{noteId}/card")
  public ApiResponse<ReviewCardResponse> card(@PathVariable long noteId) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(MistakeReviewResponseMapper.toCardResponse(
        requiredReviewSessionService().cardDetail(userId, noteId)));
  }

  @GetMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH + "/{noteId}/recall/intervals")
  public ApiResponse<List<ReviewIntervalPreviewResponse>> recallIntervals(@PathVariable long noteId) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(MistakeReviewResponseMapper.toIntervalPreviewResponses(
        requiredReviewSessionService().intervalPreviews(userId, noteId)));
  }

  @GetMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH + "/{noteId}"
      + ApiContractConstants.MISTAKE_NOTES_PROBLEM_STATEMENT_PATH_SUFFIX)
  public ResponseEntity<ApiResponse<ReviewProblemStatementResponse>> problemStatement(@PathVariable long noteId) {
    long userId = requireCurrentUserId();
    String problemSlug = requiredMistakeNoteService().get(userId, noteId).problemSlug();
    ReviewProblemStatementResponse response = requiredReviewProblemCatalog().findBySlug(problemSlug)
        .map(ReviewProblemStatementResponseMapper::toResponse)
        .orElseThrow(() -> new MistakeReviewException("MISTAKE_NOTE_PROBLEM_NOT_FOUND", "未找到题目原文。"));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePrivate())
        .body(ApiResponse.success(response));
  }

  @PostMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH + "/{noteId}/recall")
  public ApiResponse<RecallReviewResponse> recall(
      @PathVariable long noteId,
      @Valid @RequestBody SubmitRecallRequest request
  ) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(MistakeReviewResponseMapper.toRecallResponse(
        requiredReviewSessionService().submitRecall(userId, noteId, request.recallText(), request.transientNote())));
  }

  @PostMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH + "/{noteId}/recall/evaluation")
  public ApiResponse<RecallEvaluationResponse> evaluateRecall(
      @PathVariable long noteId,
      @Valid @RequestBody SubmitRecallRequest request
  ) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(MistakeReviewResponseMapper.toEvaluationResponse(
        requiredReviewSessionService().evaluateRecall(userId, noteId, request.recallText(), request.transientNote())));
  }

  @PostMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH + "/{noteId}/recall/confirm")
  public ApiResponse<RecallConfirmResponse> confirmRecall(
      @PathVariable long noteId,
      @Valid @RequestBody ConfirmRecallRequest request
  ) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(MistakeReviewResponseMapper.toConfirmResponse(
        requiredReviewSessionService().confirmRecall(
            userId,
            noteId,
            request.evaluationId(),
            ReviewRating.parse(request.rating()))));
  }

  @PostMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH + "/{noteId}"
      + ApiContractConstants.MISTAKE_NOTES_RECALL_RATING_PATH_SUFFIX)
  public ApiResponse<RecallConfirmResponse> rateRecall(
      @PathVariable long noteId,
      @Valid @RequestBody RateRecallRequest request
  ) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(MistakeReviewResponseMapper.toConfirmResponse(
        requiredReviewSessionService().rateRecall(userId, noteId, ReviewRating.parse(request.rating()))));
  }

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new MistakeReviewUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }

  private MistakeNoteService requiredMistakeNoteService() {
    return mistakeNoteService.getIfAvailable(() -> {
      throw new MistakeReviewException("MISTAKE_NOTE_SERVICE_UNAVAILABLE", "错题本服务不可用。");
    });
  }

  private ReviewSessionService requiredReviewSessionService() {
    return reviewSessionService.getIfAvailable(() -> {
      throw new MistakeReviewException("REVIEW_SESSION_SERVICE_UNAVAILABLE", "复习会话服务不可用。");
    });
  }

  private ReviewProblemCatalog requiredReviewProblemCatalog() {
    return reviewProblemCatalog.getIfAvailable(() -> {
      throw new MistakeReviewException("MISTAKE_NOTE_PROBLEM_NOT_FOUND", "未找到题目原文。");
    });
  }

  private MistakeSource parseSource(String source) {
    if (source == null || source.isBlank()) {
      return null;
    }
    try {
      return MistakeSource.valueOf(source.trim());
    } catch (IllegalArgumentException exception) {
      throw new MistakeReviewException("MISTAKE_NOTE_INVALID_FILTER", "错题来源筛选参数不合法。");
    }
  }
}
