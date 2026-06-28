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
   * Agent 工具权限决策提交路径。
   */
  public static final String AGENT_TOOL_PERMISSION_DECISION_PATH =
      "/api/agent/tool-permissions/{permissionRequestId}/decision";

  /**
   * Agent conversation 流式运行路径。
   */
  public static final String STREAM_PATH = "/stream";

  /**
   * 健康检查接口路径。
   */
  public static final String HEALTH_PATH = "/api/health";

  /**
   * 当前用户能力画像接口根路径。
   */
  public static final String ABILITIES_PROFILE_PATH = "/api/abilities/profile";

  /**
   * 题库接口根路径。
   */
  public static final String PROBLEMS_BASE_PATH = "/api/problems";

  /**
   * 题库内容语言请求参数名。
   */
  public static final String PROBLEM_LOCALE_PARAM = "locale";

  /**
   * 学习计划接口根路径。
   */
  public static final String LEARNING_PLANS_BASE_PATH = "/api/learning-plans";

  /**
   * 学习计划草案集合路径。
   */
  public static final String LEARNING_PLAN_DRAFTS_PATH = "/drafts";

  /**
   * 学习计划草案流式创建路径。
   */
  public static final String LEARNING_PLAN_DRAFTS_STREAM_PATH = "/drafts/stream";

  /**
   * 学习计划草案消息路径。
   */
  public static final String LEARNING_PLAN_DRAFT_MESSAGES_PATH = "/{draftId}/messages";

  /**
   * 学习计划草案确认路径。
   */
  public static final String LEARNING_PLAN_DRAFT_CONFIRM_PATH = "/{draftId}/confirm";

  /**
   * 题目练习会话接口根路径。
   */
  public static final String PRACTICE_SESSIONS_BASE_PATH = "/api/practice-sessions";

  /**
   * 学习计划题目的练习会话创建路径。
   */
  public static final String LEARNING_PLAN_PROBLEM_PRACTICE_SESSION_PATH =
      "/{planId}/phases/{phaseIndex}/problems/{slug}/practice-session";

  /**
   * 题目练习会话消息流式路径。
   */
  public static final String PRACTICE_SESSION_MESSAGES_STREAM_PATH = "/{sessionId}/messages/stream";

  /**
   * 题目练习会话 active run 查询路径。
   */
  public static final String PRACTICE_SESSION_ACTIVE_RUN_PATH = "/{sessionId}/active-run";

  /**
   * 题目练习会话历史消息查询路径。
   */
  public static final String PRACTICE_SESSION_MESSAGES_PATH = "/{sessionId}/messages";

  /**
   * 题目练习会话进度状态路径。
   */
  public static final String PRACTICE_SESSION_PROGRESS_STATUS_PATH = "/{sessionId}/progress-status";

  /**
   * 题目练习代码 Review 历史路径。
   */
  public static final String PRACTICE_SESSION_REVIEWS_PATH = "/{sessionId}/reviews";

  /**
   * 题目练习代码 Review 详情路径。
   */
  public static final String PRACTICE_SESSION_REVIEW_DETAIL_PATH = "/{sessionId}/reviews/{reviewId}";

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
