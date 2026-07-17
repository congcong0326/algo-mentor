package org.congcong.algomentor.api.controller.feedback;

import java.util.Locale;
import org.congcong.algomentor.api.controller.admin.feedback.AdminFeedbackController;
import org.congcong.algomentor.api.feedback.service.FeedbackErrorCode;
import org.congcong.algomentor.api.feedback.service.FeedbackException;
import org.congcong.algomentor.api.feedback.service.FeedbackUnauthenticatedException;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {FeedbackController.class, AdminFeedbackController.class})
public class FeedbackExceptionHandler {
  private final ApiErrorResponseFactory factory;
  public FeedbackExceptionHandler(ObjectProvider<ApiErrorResponseFactory> factoryProvider) {
    this.factory = factoryProvider.getIfAvailable(() -> new ApiErrorResponseFactory(new ApiErrorMessageResolver()));
  }
  @ExceptionHandler(FeedbackUnauthenticatedException.class) public ResponseEntity<ApiResponse<Void>> unauthenticated(FeedbackUnauthenticatedException exception, Locale locale) { return failure(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHENTICATED", exception.getMessage(), locale); }
  @ExceptionHandler(FeedbackException.class) public ResponseEntity<ApiResponse<Void>> feedback(FeedbackException exception, Locale locale) { return failure(status(exception.code()), exception.code().name(), exception.getMessage(), locale); }
  private ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String code, String message, Locale locale) { return ResponseEntity.status(status).body(factory.failure(code, message, locale)); }
  private HttpStatus status(FeedbackErrorCode code) { return switch (code) { case FEEDBACK_THREAD_NOT_FOUND -> HttpStatus.NOT_FOUND; case FEEDBACK_THREAD_FORBIDDEN -> HttpStatus.FORBIDDEN; default -> HttpStatus.BAD_REQUEST; }; }
}
