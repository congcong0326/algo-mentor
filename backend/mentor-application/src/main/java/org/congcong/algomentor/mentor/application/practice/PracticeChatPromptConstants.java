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
  public static final String SECTION_SCENARIO_POLICY = "practice.strategy.coach";
  public static final String SECTION_RUNTIME_CONTEXT = "practice.context.training";
  public static final String SECTION_ACTIVE_SUMMARY = "practice.memory.active-summary";
  public static final String SECTION_CURRENT_USER_MESSAGE = "practice.current-user-message";
  public static final String SECTION_HISTORY_PREFIX = "practice.history.";

  public static final String MESSAGE_TYPE_METADATA_KEY = "messageType";
  public static final String MESSAGE_TYPE_PROBLEM_STATEMENT = "PROBLEM_STATEMENT";
  public static final String MESSAGE_TYPE_CHAT = "CHAT";

  public static final String VARIABLE_CONTEXT = "practiceContext";
  public static final String VARIABLE_CURRENT_USER_MESSAGE = "currentUserMessage";
  public static final String VARIABLE_ACTIVE_SUMMARY = "activeSummary";
  public static final String VARIABLE_HISTORY = "history";

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

  private PracticeChatPromptConstants() {
  }
}
