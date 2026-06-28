package org.congcong.algomentor.api.controller;

import jakarta.validation.ConstraintViolationException;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionException;
import org.congcong.algomentor.api.controller.ability.AbilityProfileUnauthenticatedException;
import org.congcong.algomentor.api.controller.learningplan.LearningPlanUnauthenticatedException;
import org.congcong.algomentor.api.controller.preference.UserAiPreferenceUnauthenticatedException;
import org.congcong.algomentor.api.controller.practice.PracticeProgressStatusInvalidException;
import org.congcong.algomentor.api.controller.practice.PracticeSessionUnauthenticatedException;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunInProgressException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class LocalizedApiExceptionHandler {

  public static final String AUTH_UNAUTHENTICATED_CODE = "AUTH_UNAUTHENTICATED";
  public static final String PROBLEM_NOT_FOUND_CODE = "PROBLEM_NOT_FOUND";
  public static final String UNSUPPORTED_PROBLEM_LOCALE_CODE = "UNSUPPORTED_PROBLEM_LOCALE";
  public static final String PROBLEM_REPOSITORY_UNAVAILABLE_CODE = "PROBLEM_REPOSITORY_UNAVAILABLE";
  public static final String PRACTICE_MESSAGE_INVALID_CODE = "PRACTICE_MESSAGE_INVALID";
  public static final String PRACTICE_PROGRESS_STATUS_INVALID_CODE = "PRACTICE_PROGRESS_STATUS_INVALID";
  public static final String VALIDATION_FAILED_CODE = "VALIDATION_FAILED";
  public static final String REQUEST_BODY_INVALID_CODE = "REQUEST_BODY_INVALID";
  public static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";

  private static final Logger log = LoggerFactory.getLogger(LocalizedApiExceptionHandler.class);

  private final ApiErrorResponseFactory responseFactory;

  public LocalizedApiExceptionHandler() {
    this(new ApiErrorResponseFactory(new ApiErrorMessageResolver()));
  }

  public LocalizedApiExceptionHandler(ApiErrorResponseFactory responseFactory) {
    this.responseFactory = responseFactory;
  }

  @ExceptionHandler(org.congcong.algomentor.api.controller.problem.ProblemController.ProblemNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> problemNotFound(RuntimeException exception) {
    return failure(HttpStatus.NOT_FOUND, PROBLEM_NOT_FOUND_CODE, exception.getMessage());
  }

  @ExceptionHandler(ProblemService.ProblemRepositoryUnavailableException.class)
  public ResponseEntity<ApiResponse<Void>> problemRepositoryUnavailable(RuntimeException exception) {
    return failure(HttpStatus.SERVICE_UNAVAILABLE, PROBLEM_REPOSITORY_UNAVAILABLE_CODE, exception.getMessage());
  }

  @ExceptionHandler(ProblemLocale.UnsupportedProblemLocaleException.class)
  public ResponseEntity<ApiResponse<Void>> unsupportedProblemLocale(RuntimeException exception) {
    return failure(HttpStatus.BAD_REQUEST, UNSUPPORTED_PROBLEM_LOCALE_CODE, exception.getMessage());
  }

  @ExceptionHandler({
      LearningPlanUnauthenticatedException.class,
      PracticeSessionUnauthenticatedException.class,
      AbilityProfileUnauthenticatedException.class,
      UserAiPreferenceUnauthenticatedException.class
  })
  public ResponseEntity<ApiResponse<Void>> unauthenticated(RuntimeException exception) {
    return failure(HttpStatus.UNAUTHORIZED, AUTH_UNAUTHENTICATED_CODE, exception.getMessage());
  }

  @ExceptionHandler(LearningPlanException.class)
  public ResponseEntity<ApiResponse<Void>> learningPlanException(LearningPlanException exception) {
    return failure(HttpStatus.BAD_REQUEST, exception.code(), exception.messageKey(), exception.getMessage());
  }

  @ExceptionHandler(AgentConversationRunInProgressException.class)
  public ResponseEntity<ApiResponse<Void>> agentRunInProgress(AgentConversationRunInProgressException exception) {
    return failure(HttpStatus.CONFLICT, exception.code(), exception.getMessage(), exception.metadata());
  }

  @ExceptionHandler(AiRunAdmissionException.class)
  public ResponseEntity<ApiResponse<Void>> aiRunAdmission(AiRunAdmissionException exception) {
    return failure(exception.suggestedStatus(), exception.code().name(), exception.getMessage(), exception.metadata());
  }

  @ExceptionHandler(PracticeProgressStatusInvalidException.class)
  public ResponseEntity<ApiResponse<Void>> invalidProgressStatus(RuntimeException exception) {
    return failure(HttpStatus.BAD_REQUEST, PRACTICE_PROGRESS_STATUS_INVALID_CODE, exception.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> invalidRequestBody(HttpMessageNotReadableException exception) {
    return failure(HttpStatus.BAD_REQUEST, REQUEST_BODY_INVALID_CODE, "请求体不是合法 JSON 或与接口结构不匹配。");
  }

  @ExceptionHandler({
      MethodArgumentNotValidException.class,
      HandlerMethodValidationException.class,
      ConstraintViolationException.class,
      MissingServletRequestParameterException.class,
      MethodArgumentTypeMismatchException.class,
      BindException.class
  })
  public ResponseEntity<ApiResponse<Void>> validationFailed(Exception exception) {
    return failure(HttpStatus.BAD_REQUEST, validationCode(exception), validationFallback(exception));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> internalError(Exception exception) {
    log.error("Unhandled HTTP API exception", exception);
    return failure(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_CODE, "服务暂时不可用，请稍后再试。");
  }

  private ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String code, String fallbackMessage) {
    return failure(status, code, null, fallbackMessage);
  }

  private ResponseEntity<ApiResponse<Void>> failure(
      HttpStatus status,
      String code,
      String messageKey,
      String fallbackMessage
  ) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(responseFactory.failure(
            code,
            messageKey,
            fallbackMessage,
            java.util.Map.of(),
            LocaleContextHolder.getLocale()));
  }

  private ResponseEntity<ApiResponse<Void>> failure(
      HttpStatus status,
      String code,
      String fallbackMessage,
      java.util.Map<String, Object> metadata
  ) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(responseFactory.failure(code, fallbackMessage, metadata, LocaleContextHolder.getLocale()));
  }

  private String validationCode(Exception exception) {
    if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException
        && methodArgumentNotValidException.getParameter().getContainingClass().equals(
            org.congcong.algomentor.api.controller.practice.PracticeSessionController.class)) {
      return PRACTICE_MESSAGE_INVALID_CODE;
    }
    return VALIDATION_FAILED_CODE;
  }

  private String validationFallback(Exception exception) {
    if (PRACTICE_MESSAGE_INVALID_CODE.equals(validationCode(exception))) {
      return "练习消息不能为空。";
    }
    return "请求参数校验失败。";
  }
}
