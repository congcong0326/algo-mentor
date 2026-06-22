package org.congcong.algomentor.auth.controller;

import org.congcong.algomentor.auth.model.CurrentUserResponse;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AuthApiContractConstants.AUTH_API_BASE_PATH)
public class CurrentUserController {

  private final CurrentUserIdProvider currentUserIdProvider;

  public CurrentUserController(CurrentUserIdProvider currentUserIdProvider) {
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @GetMapping(AuthApiContractConstants.ME_PATH)
  public ResponseEntity<ApiResponse<CurrentUserResponse>> me() {
    return currentUserIdProvider.currentUser()
        .map(principal -> ResponseEntity.ok(ApiResponse.success(new CurrentUserResponse(
            principal.userId(),
            principal.email(),
            principal.displayName(),
            principal.avatarUrl(),
            principal.roles(),
            principal.status()))))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.failure(
                AuthApiContractConstants.AUTH_UNAUTHENTICATED_CODE,
                "当前请求未登录或无法解析当前用户。")));
  }
}
