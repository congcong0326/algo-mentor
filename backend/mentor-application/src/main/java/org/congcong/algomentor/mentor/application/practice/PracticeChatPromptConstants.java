package org.congcong.algomentor.mentor.application.practice;

/**
 * 题目聊天 prompt profile、section 和 metadata 稳定契约。
 */
public final class PracticeChatPromptConstants {

  public static final String SCENARIO = "PRACTICE_CHAT";
  public static final String PROFILE_ID = "PRACTICE_CHAT_V1";
  public static final String PROFILE_VERSION = "2026-06-24";
  public static final String POLICY_NAME = "practice-chat-prompt-assembly";
  public static final String POLICY_VERSION = "v1";
  public static final int DEFAULT_TOKEN_BUDGET = 8_000;

  public static final String SECTION_BASE_INSTRUCTION = "practice.base.identity";
  public static final String SECTION_COACH_STYLE = "practice.strategy.coach-style";
  public static final String SECTION_RESPONSE_LANGUAGE = "practice.strategy.response-language";
  public static final String SECTION_SCENARIO_POLICY = "practice.strategy.coach";
  public static final String SECTION_RUNTIME_CONTEXT = "practice.context.training";
  public static final String SECTION_ACTIVE_SUMMARY = "practice.memory.active-summary";
  /** 单次运行固定的学习者画像参考 section，不查询数据库。 */
  public static final String SECTION_LEARNER_PROFILE = "practice.memory.learner-profile";
  public static final String SECTION_CURRENT_USER_MESSAGE = "practice.current-user-message";
  public static final String SECTION_HISTORY_PREFIX = "practice.history.";

  public static final String MESSAGE_TYPE_METADATA_KEY = "messageType";
  public static final String MESSAGE_TYPE_PROBLEM_STATEMENT = "PROBLEM_STATEMENT";
  public static final String MESSAGE_TYPE_CHAT = "CHAT";

  public static final String VARIABLE_CONTEXT = "practiceContext";
  public static final String VARIABLE_CURRENT_USER_MESSAGE = "currentUserMessage";
  public static final String VARIABLE_ACTIVE_SUMMARY = "activeSummary";
  public static final String VARIABLE_HISTORY = "history";
  public static final String VARIABLE_COACH_STYLE = "coachStyle";
  public static final String VARIABLE_RESPONSE_LANGUAGE = "responseLanguage";
  /** loop 前读取一次的学习者画像快照，仅供 Prompt provider 渲染。 */
  public static final String VARIABLE_LEARNER_PROFILE_SNAPSHOT = "learnerProfileSnapshot";

  /**
   * Agent 场景标识，用于把普通会话切换到题目训练聊天 prompt/profile 和治理观测语义。
   */
  public static final String METADATA_SCENARIO = "scenario";
  /**
   * 当前练习会话 ID，用于把 Agent trace 与题目训练会话关联。
   */
  public static final String METADATA_PRACTICE_SESSION_ID = "practiceSessionId";
  /**
   * 当前学习计划 ID，用于按用户恢复计划上下文，并关联 trace/debug metadata。
   */
  public static final String METADATA_PLAN_ID = "planId";
  /**
   * 当前学习计划阶段序号，用于定位阶段目标、训练重点和阶段内题目。
   */
  public static final String METADATA_PHASE_INDEX = "phaseIndex";
  /**
   * 当前训练题目的 slug，用于校验题目属于该阶段，并加载题面、难度、标签等题库详情。
   */
  public static final String METADATA_PROBLEM_SLUG = "problemSlug";
  /**
   * 当前题目聊天语言，用于选择题面本地化版本，并作为 prompt 中的回复语言参考。
   */
  public static final String METADATA_LOCALE = "locale";
  /**
   * 本轮用户消息意图分类，用于在题目聊天 prompt 中调整教练策略。
   */
  public static final String METADATA_MESSAGE_INTENT = "messageIntent";
  /**
   * 当前用户选择的教练风格，用于每轮动态注入受控 style prompt。
   */
  public static final String METADATA_COACH_STYLE = "coachStyle";
  /**
   * 当前请求语境推导出的 AI 回复语言，用于每轮动态注入语言约束。
   */
  public static final String METADATA_RESPONSE_LANGUAGE = "responseLanguage";
  /** 画像 section 实际注入的 token 估算，不包含画像正文。 */
  public static final String METADATA_LEARNER_PROFILE_TOKEN_ESTIMATE = "learnerProfileTokenEstimate";
  /** 画像 section 是否因其 800 token 独立预算或总 Prompt 预算发生裁剪。 */
  public static final String METADATA_LEARNER_PROFILE_TRIMMED = "learnerProfileTrimmed";
  /** 画像快照中参与渲染的条目数，不包含条目正文。 */
  public static final String METADATA_LEARNER_PROFILE_ENTRY_COUNT = "learnerProfileEntryCount";

  private PracticeChatPromptConstants() {
  }
}
