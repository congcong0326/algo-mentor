package org.congcong.algomentor.api.controller.learningplan;

import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LearningPlanController.class)
public class LearningPlanExceptionHandler {

  public static final String AUTH_UNAUTHENTICATED_CODE = "AUTH_UNAUTHENTICATED";

  @ExceptionHandler(LearningPlanUnauthenticatedException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiResponse<Void> unauthenticated(LearningPlanUnauthenticatedException exception) {
    return ApiResponse.failure(AUTH_UNAUTHENTICATED_CODE, exception.getMessage());
  }

  @ExceptionHandler(LearningPlanException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> learningPlanException(LearningPlanException exception) {
    return ApiResponse.failure(exception.code(), exception.getMessage());
  }
}
