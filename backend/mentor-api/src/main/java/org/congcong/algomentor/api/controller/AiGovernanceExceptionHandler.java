package org.congcong.algomentor.api.controller;

import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionException;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AiGovernanceExceptionHandler {

  @ExceptionHandler(AiRunAdmissionException.class)
  public ResponseEntity<ApiResponse<Void>> admission(AiRunAdmissionException exception) {
    return ResponseEntity.status(exception.suggestedStatus())
        .contentType(MediaType.APPLICATION_JSON)
        .body(ApiResponse.failure(exception.code().name(), exception.getMessage(), exception.metadata()));
  }
}
