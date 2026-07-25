package org.congcong.algomentor.api.controller.review;

import jakarta.validation.Valid;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.review.model.ReviewResponseMapper;
import org.congcong.algomentor.api.review.model.UpsertUserProblemNoteRequest;
import org.congcong.algomentor.api.review.model.UserProblemNoteResponse;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.review.ReviewException;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserProblemNoteController {

  private final ObjectProvider<UserProblemNoteService> noteService;
  private final CurrentUserIdProvider currentUserIdProvider;

  public UserProblemNoteController(
      ObjectProvider<UserProblemNoteService> noteService,
      CurrentUserIdProvider currentUserIdProvider
  ) {
    this.noteService = noteService;
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @GetMapping(ApiContractConstants.USER_PROBLEMS_BASE_PATH + "/{problemSlug}/note")
  public ApiResponse<UserProblemNoteResponse> get(@PathVariable String problemSlug) {
    return ApiResponse.success(ReviewResponseMapper.toNoteResponse(
        requiredNoteService().get(requireCurrentUserId(), problemSlug)));
  }

  @PutMapping(ApiContractConstants.USER_PROBLEMS_BASE_PATH + "/{problemSlug}/note")
  public ApiResponse<UserProblemNoteResponse> upsert(
      @PathVariable String problemSlug,
      @Valid @RequestBody UpsertUserProblemNoteRequest request
  ) {
    return ApiResponse.success(ReviewResponseMapper.toNoteResponse(requiredNoteService().upsert(
        requireCurrentUserId(),
        problemSlug,
        request.outline(),
        request.noteMarkdown(),
        request.expectedRevision())));
  }

  @DeleteMapping(ApiContractConstants.USER_PROBLEMS_BASE_PATH + "/{problemSlug}/note")
  public ApiResponse<Void> delete(@PathVariable String problemSlug) {
    requiredNoteService().delete(requireCurrentUserId(), problemSlug);
    return ApiResponse.success(null);
  }

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new ReviewUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }

  private UserProblemNoteService requiredNoteService() {
    return noteService.getIfAvailable(() -> {
      throw new ReviewException("PROBLEM_NOTE_SERVICE_UNAVAILABLE", "题目笔记服务不可用。");
    });
  }
}
