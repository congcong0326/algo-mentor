package org.congcong.algomentor.api.controller.knowledge;

import static org.congcong.algomentor.api.knowledge.model.KnowledgeContract.*;

import org.congcong.algomentor.api.knowledge.service.KnowledgeException;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = KnowledgeController.class)
@Order(0)
public class KnowledgeExceptionHandler {
  @ExceptionHandler(KnowledgeException.class)
  public ResponseEntity<?> knowledge(KnowledgeException e) {
    return ResponseEntity.status(e.status()).body(ApiResponse.failure(e.code(), e.getMessage()));
  }

  @ExceptionHandler({
    org.congcong.algomentor.mentor.application.review.ReviewException.class,
    IllegalArgumentException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest().body(ApiResponse.failure(INVALID_REQUEST, "请求参数无效"));
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<?> database(DataAccessException e) {
    return ResponseEntity.status(503).body(ApiResponse.failure(UNAVAILABLE, "知识库暂时不可用，请稍后重试"));
  }
}
