package org.congcong.algomentor.api.controller.preference;

import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.preference.model.UserAiPreferenceRequest;
import org.congcong.algomentor.api.preference.model.UserAiPreferenceResponse;
import org.congcong.algomentor.api.preference.model.UserAiPreferenceResponseMapper;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceService;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceUpdate;
import org.congcong.algomentor.mentor.application.practice.PracticeCoachStyle;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserAiPreferenceController {

  private final UserAiPreferenceService preferenceService;
  private final CurrentUserIdProvider currentUserIdProvider;

  public UserAiPreferenceController(
      UserAiPreferenceService preferenceService,
      CurrentUserIdProvider currentUserIdProvider
  ) {
    this.preferenceService = preferenceService;
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @GetMapping(ApiContractConstants.ME_AI_PREFERENCES_PATH)
  public ApiResponse<UserAiPreferenceResponse> get() {
    return ApiResponse.success(UserAiPreferenceResponseMapper.toResponse(
        preferenceService.get(requireCurrentUserId())));
  }

  @PatchMapping(ApiContractConstants.ME_AI_PREFERENCES_PATH)
  public ApiResponse<UserAiPreferenceResponse> update(@RequestBody UserAiPreferenceRequest request) {
    return ApiResponse.success(UserAiPreferenceResponseMapper.toResponse(preferenceService.update(
        requireCurrentUserId(),
        new UserAiPreferenceUpdate(
            PracticeCoachStyle.from(request == null ? null : request.coachStyle())))));
  }

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new UserAiPreferenceUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }
}
