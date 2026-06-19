package org.congcong.algomentor.api.controller;

import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunInProgressException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AgentConversationController.class)
public class AgentConversationExceptionHandler {

  @ExceptionHandler(AgentConversationRunInProgressException.class)
  public ResponseEntity<ApiResponse<Void>> agentRunInProgress(AgentConversationRunInProgressException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .contentType(MediaType.APPLICATION_JSON)
        .body(ApiResponse.failure(exception.code(), exception.getMessage(), exception.metadata()));
  }
}
