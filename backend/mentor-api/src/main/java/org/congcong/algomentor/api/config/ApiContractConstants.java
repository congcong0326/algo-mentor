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
   * 当前用户 AI 偏好设置路径。
   */
  public static final String ME_AI_PREFERENCES_PATH = "/api/me/ai-preferences";

  /**
   * 当前用户复习/FSRS 偏好设置路径。
   */
  public static final String ME_REVIEW_PREFERENCES_PATH = "/api/me/review-preferences";

  /**
   * 管理员题库查询接口根路径。
   */
  public static final String PROBLEMS_BASE_PATH = "/api/admin/problems";

  /**
   * 题库内容语言请求参数名。
   */
  public static final String PROBLEM_LOCALE_PARAM = "locale";

  /**
   * 学习计划接口根路径。
   */
  public static final String LEARNING_PLANS_BASE_PATH = "/api/learning-plans";

  /**
   * 学习计划模板接口根路径。
   */
  public static final String LEARNING_PLAN_TEMPLATES_BASE_PATH = "/api/learning-plan-templates";

  /**
   * 学习计划草案集合路径。
   */
  public static final String LEARNING_PLAN_DRAFTS_PATH = "/drafts";

  /**
   * 从学习计划模板生成草案路径。
   */
  public static final String LEARNING_PLAN_DRAFT_FROM_TEMPLATE_PATH = "/drafts/from-template";

  /**
   * 学习计划草案流式创建路径。
   */
  public static final String LEARNING_PLAN_DRAFTS_STREAM_PATH = "/drafts/stream";

  /**
   * 学习计划草案修订流式创建路径。
   */
  public static final String LEARNING_PLAN_DRAFT_REVISIONS_STREAM_PATH = "/{draftId}/revisions/stream";

  /**
   * 学习计划草案消息路径。
   */
  public static final String LEARNING_PLAN_DRAFT_MESSAGES_PATH = "/{draftId}/messages";

  /**
   * 学习计划草案确认路径。
   */
  public static final String LEARNING_PLAN_DRAFT_CONFIRM_PATH = "/{draftId}/confirm";

  /**
   * 学习计划扩展提案首次生成流式路径。
   */
  public static final String LEARNING_PLAN_EXTENSION_PROPOSALS_STREAM_PATH =
      "/{planId}/extension-proposals/stream";

  /**
   * 学习计划扩展提案修订流式路径。
   */
  public static final String LEARNING_PLAN_EXTENSION_PROPOSAL_REVISIONS_STREAM_PATH =
      "/{planId}/extension-proposals/{proposalGroupId}/revisions/stream";

  /**
   * 学习计划扩展提案应用路径。
   */
  public static final String LEARNING_PLAN_EXTENSION_PROPOSAL_APPLY_PATH =
      "/{planId}/extension-proposals/{proposalGroupId}/apply";

  /**
   * 学习计划扩展提案丢弃路径。
   */
  public static final String LEARNING_PLAN_EXTENSION_PROPOSAL_DISCARD_PATH =
      "/{planId}/extension-proposals/{proposalGroupId}/discard";

  /**
   * 学习计划契约暂停路径。
   */
  public static final String LEARNING_PLAN_CONTRACT_PAUSE_PATH = "/{planId}/contract/pause";

  /**
   * 学习计划契约恢复路径。
   */
  public static final String LEARNING_PLAN_CONTRACT_RESUME_PATH = "/{planId}/contract/resume";

  /**
   * 学习计划契约主动收尾路径。
   */
  public static final String LEARNING_PLAN_CONTRACT_CLOSE_OUT_PATH = "/{planId}/contract/close-out";

  /**
   * 学习计划训练节奏更新路径。
   */
  public static final String LEARNING_PLAN_RHYTHM_PATH = "/{planId}/rhythm";

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
   * 错题本接口根路径。
   */
  public static final String MISTAKE_NOTES_BASE_PATH = "/api/mistake-notes";

  /**
   * 错题题面详情路径后缀。
   */
  public static final String MISTAKE_NOTES_PROBLEM_STATEMENT_PATH_SUFFIX = "/problem-statement";

  /**
   * 错题复述直接自评提交路径后缀。
   */
  public static final String MISTAKE_NOTES_RECALL_RATING_PATH_SUFFIX = "/recall/rating";

  /**
   * 复习会话接口根路径。
   */
  public static final String REVIEW_SESSIONS_BASE_PATH = "/api/review-sessions";

  /**
   * 主题讲解请求参数名。
   */
  public static final String TOPIC_PARAM = "topic";

  /**
   * 幂等请求头名。
   */
  public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

  /**
   * 请求语境语言头名，用于错误本地化和 AI 回复语言动态注入。
   */
  public static final String ACCEPT_LANGUAGE_HEADER = "Accept-Language";

  private ApiContractConstants() {
  }
}
