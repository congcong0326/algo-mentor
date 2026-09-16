package org.congcong.algomentor.api.knowledge.service;

import static org.congcong.algomentor.api.knowledge.model.KnowledgeContract.*;

/** 知识库资源和评价契约错误。 */
public class KnowledgeException extends RuntimeException {
  private final String code;
  private final int status;

  private KnowledgeException(String code, int status, String message) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }

  public static KnowledgeException notFound() {
    return new KnowledgeException(NOT_FOUND, 404, "知识内容不存在或尚未发布");
  }

  public static KnowledgeException conflict() {
    return new KnowledgeException(ATTEMPT_CONFLICT, 409, "同一评价请求标识不能用于不同卡片或评级");
  }
}
