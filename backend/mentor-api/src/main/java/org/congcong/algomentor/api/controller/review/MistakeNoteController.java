package org.congcong.algomentor.api.controller.review;

import jakarta.validation.Valid;
import java.util.List;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.review.model.ArchiveMistakeRequest;
import org.congcong.algomentor.api.review.model.MarkMistakeRequest;
import org.congcong.algomentor.api.review.model.MistakeNoteResponse;
import org.congcong.algomentor.api.review.model.MistakeReviewResponseMapper;
import org.congcong.algomentor.api.review.model.RecallReviewResponse;
import org.congcong.algomentor.api.review.model.ReviewCardResponse;
import org.congcong.algomentor.api.review.model.SubmitRecallRequest;
import org.congcong.algomentor.api.review.model.UpdateMistakeNoteRequest;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.review.MasteryState;
import org.congcong.algomentor.mentor.application.review.MistakeNoteService;
import org.congcong.algomentor.mentor.application.review.MistakeReviewException;
import org.congcong.algomentor.mentor.application.review.MistakeSource;
import org.congcong.algomentor.mentor.application.review.ReviewSessionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MistakeNoteController {

  private final ObjectProvider<MistakeNoteService> mistakeNoteService;
  private final ObjectProvider<ReviewSessionService> reviewSessionService;
  private final CurrentUserIdProvider currentUserIdProvider;

  public MistakeNoteController(
      ObjectProvider<MistakeNoteService> mistakeNoteService,
      ObjectProvider<ReviewSessionService> reviewSessionService,
      CurrentUserIdProvider currentUserIdProvider
  ) {
    this.mistakeNoteService = mistakeNoteService;
    this.reviewSessionService = reviewSessionService;
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @GetMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH)
  public ApiResponse<List<MistakeNoteResponse>> list(
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset
  ) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(MistakeReviewResponseMapper.toNoteResponses(requiredMistakeNoteService().list(
        userId,
        parseState(state),
        parseSource(source),
        keyword,
        limit,
        offset)));
  }

  @PostMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH)
  public ApiResponse<MistakeNoteResponse> mark(@Valid @RequestBody MarkMistakeRequest request) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(MistakeReviewResponseMapper.toNoteResponse(
        requiredMistakeNoteService().mark(userId, request.problemSlug())));
  }

  @PatchMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH + "/{noteId}/archive")
  public ApiResponse<MistakeNoteResponse> archive(
      @PathVariable long noteId,
      @RequestBody ArchiveMistakeRequest request
  ) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(MistakeReviewResponseMapper.toNoteResponse(
        requiredMistakeNoteService().archive(userId, noteId, request.archived())));
  }

  @PatchMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH + "/{noteId}/note")
  public ApiResponse<MistakeNoteResponse> updateNote(
      @PathVariable long noteId,
      @RequestBody UpdateMistakeNoteRequest request
  ) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(MistakeReviewResponseMapper.toNoteResponse(
        requiredMistakeNoteService().updatePersistentNote(userId, noteId, request.text())));
  }

  @GetMapping(ApiContractConstants.MISTAKE_NOTES_BASE_PATH + "/{noteId}/card")
  public ApiResponse<ReviewCardResponse> card(@PathVariable long noteId) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(MistakeReviewResponseMapper.toCardResponse(
        requiredReviewSessionService().card(userId, noteId)));
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

  private MasteryState parseState(String state) {
    if (state == null || state.isBlank()) {
      return null;
    }
    try {
      return MasteryState.valueOf(state.trim());
    } catch (IllegalArgumentException exception) {
      throw new MistakeReviewException("MISTAKE_NOTE_INVALID_FILTER", "掌握状态筛选参数不合法。");
    }
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
