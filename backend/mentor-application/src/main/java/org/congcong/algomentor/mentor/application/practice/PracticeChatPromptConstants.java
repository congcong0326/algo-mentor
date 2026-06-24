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

  public static final String METADATA_SCENARIO = "scenario";
  public static final String METADATA_PLAN_ID = "planId";
  public static final String METADATA_PHASE_INDEX = "phaseIndex";
  public static final String METADATA_PROBLEM_SLUG = "problemSlug";
  public static final String METADATA_LOCALE = "locale";
  public static final String METADATA_MESSAGE_INTENT = "messageIntent";

  private PracticeChatPromptConstants() {
  }
}
