package org.congcong.algomentor.api.controller.admin.ai;

import java.util.Locale;
import org.congcong.algomentor.ai.governance.model.AiGovernanceAdminException;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimePolicyException;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
    AdminAiSettingsController.class,
    AdminUserAiPolicyController.class,
    AdminAiModelPriceController.class,
    AdminAiUsageController.class,
    AdminAiProviderController.class,
    AdminAiModelRoutingController.class
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminAiExceptionHandler {

  private final ApiErrorResponseFactory responseFactory;

  public AdminAiExceptionHandler(ObjectProvider<ApiErrorResponseFactory> responseFactoryProvider) {
    this.responseFactory = responseFactoryProvider.getIfAvailable(
        () -> new ApiErrorResponseFactory(new ApiErrorMessageResolver()));
  }

  @ExceptionHandler(AiGovernanceAdminException.class)
  public ResponseEntity<ApiResponse<Void>> governance(AiGovernanceAdminException exception, Locale locale) {
    return failure(exception.code(), exception.getMessage(), locale);
  }

  @ExceptionHandler(AiRuntimePolicyException.class)
  public ResponseEntity<ApiResponse<Void>> runtimePolicy(AiRuntimePolicyException exception, Locale locale) {
    return failure(exception.code(), exception.getMessage(), locale);
  }

  private ResponseEntity<ApiResponse<Void>> failure(
      AiGovernanceErrorCode code,
      String fallbackMessage,
      Locale locale
  ) {
    return ResponseEntity.status(status(code)).body(responseFactory.failure(code.name(), fallbackMessage, locale));
  }

  private HttpStatus status(AiGovernanceErrorCode code) {
    return switch (code) {
      case AI_GLOBALLY_DISABLED -> HttpStatus.SERVICE_UNAVAILABLE;
      case AI_USER_DISABLED -> HttpStatus.FORBIDDEN;
      case AI_MODEL_PRICE_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case AI_MODEL_PRICE_ALREADY_EXISTS -> HttpStatus.CONFLICT;
      case AI_PROVIDER_NOT_FOUND, AI_MODEL_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case AI_PROVIDER_NAME_ALREADY_EXISTS, AI_MODEL_ALREADY_EXISTS -> HttpStatus.CONFLICT;
      default -> HttpStatus.BAD_REQUEST;
    };
  }
}
