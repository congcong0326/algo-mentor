package org.congcong.algomentor.api.controller;

import org.congcong.algomentor.agent.core.permission.AgentToolPermissionException;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AgentToolPermissionController.class)
public class AgentToolPermissionExceptionHandler {

  public static final String AUTH_UNAUTHENTICATED_CODE = "AUTH_UNAUTHENTICATED";
  public static final String REQUEST_INVALID_CODE = "AGENT_TOOL_PERMISSION_REQUEST_INVALID";

  @ExceptionHandler(AgentToolPermissionUnauthenticatedException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiResponse<Void> unauthenticated(AgentToolPermissionUnauthenticatedException exception) {
    return ApiResponse.failure(AUTH_UNAUTHENTICATED_CODE, exception.getMessage());
  }

  @ExceptionHandler(AgentToolPermissionException.class)
  public ResponseEntity<ApiResponse<Void>> permissionException(AgentToolPermissionException exception) {
    return ResponseEntity.status(statusFor(exception.code()))
        .contentType(MediaType.APPLICATION_JSON)
        .body(ApiResponse.failure(exception.code().name(), exception.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> invalidRequest(HttpMessageNotReadableException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ApiResponse.failure(REQUEST_INVALID_CODE, "工具权限决策请求不合法。"));
  }

  private HttpStatus statusFor(AgentToolPermissionException.Code code) {
    return switch (code) {
      case INVALID_DECISION -> HttpStatus.BAD_REQUEST;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case ALREADY_DECIDED, EXPIRED -> HttpStatus.CONFLICT;
    };
  }
}

final class AgentToolPermissionUnauthenticatedException extends RuntimeException {

  AgentToolPermissionUnauthenticatedException(String message) {
    super(message);
  }
}
