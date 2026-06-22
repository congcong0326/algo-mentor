package org.congcong.algomentor.api.config;

/**
 * mentor-api 对外 HTTP 契约字段和路径。
 */
public final class ApiContractConstants {

  /**
   * AI 调试/讲解接口根路径。
   */
  public static final String AI_API_BASE_PATH = "/api/ai";

  /**
   * 主题讲解 SSE 路径。
   */
  public static final String AI_EXPLANATIONS_STREAM_PATH = "/explanations/stream";

  /**
   * Agent conversation 接口根路径。
   */
  public static final String AGENT_CONVERSATIONS_BASE_PATH = "/api/agent/conversations";

  /**
   * Agent conversation 流式运行路径。
   */
  public static final String STREAM_PATH = "/stream";

  /**
   * 健康检查接口路径。
   */
  public static final String HEALTH_PATH = "/api/health";

  /**
   * 题库接口根路径。
   */
  public static final String PROBLEMS_BASE_PATH = "/api/problems";

  /**
   * 学习计划接口根路径。
   */
  public static final String LEARNING_PLANS_BASE_PATH = "/api/learning-plans";

  /**
   * 学习计划草案集合路径。
   */
  public static final String LEARNING_PLAN_DRAFTS_PATH = "/drafts";

  /**
   * 学习计划草案消息路径。
   */
  public static final String LEARNING_PLAN_DRAFT_MESSAGES_PATH = "/{draftId}/messages";

  /**
   * 学习计划草案确认路径。
   */
  public static final String LEARNING_PLAN_DRAFT_CONFIRM_PATH = "/{draftId}/confirm";

  /**
   * 主题讲解请求参数名。
   */
  public static final String TOPIC_PARAM = "topic";

  /**
   * 幂等请求头名。
   */
  public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

  private ApiContractConstants() {
  }
}
