package org.congcong.algomentor.api.controller.practice;

import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunInProgressException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PracticeSessionController.class)
public class PracticeSessionExceptionHandler {

  public static final String AUTH_UNAUTHENTICATED_CODE = "AUTH_UNAUTHENTICATED";
  public static final String PRACTICE_MESSAGE_INVALID_CODE = "PRACTICE_MESSAGE_INVALID";
  public static final String PRACTICE_PROGRESS_STATUS_INVALID_CODE = "PRACTICE_PROGRESS_STATUS_INVALID";

  @ExceptionHandler(PracticeSessionUnauthenticatedException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiResponse<Void> unauthenticated(PracticeSessionUnauthenticatedException exception) {
    return ApiResponse.failure(AUTH_UNAUTHENTICATED_CODE, exception.getMessage());
  }

  @ExceptionHandler(LearningPlanException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> learningPlanException(LearningPlanException exception) {
    return ApiResponse.failure(exception.code(), exception.getMessage());
  }

  @ExceptionHandler(AgentConversationRunInProgressException.class)
  public ResponseEntity<ApiResponse<Void>> agentRunInProgress(AgentConversationRunInProgressException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ApiResponse.failure(exception.code(), exception.getMessage(), exception.metadata()));
  }

  @ExceptionHandler(PracticeProgressStatusInvalidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResponse<Void> invalidProgressStatus(PracticeProgressStatusInvalidException exception) {
    return ApiResponse.failure(PRACTICE_PROGRESS_STATUS_INVALID_CODE, exception.getMessage());
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  public ResponseEntity<ApiResponse<Void>> invalidMessage(Exception exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ApiResponse.failure(PRACTICE_MESSAGE_INVALID_CODE, "练习消息不能为空。"));
  }
}
