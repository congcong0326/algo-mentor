package org.congcong.algomentor.mentor.application.prompt;

/** 初始系统提示词场景的稳定 section key 契约。 */
public final class SystemPromptSectionKeys {

  public static final String MENTOR_CONVERSATION_BASE = "mentor-conversation.base";
  public static final String TOPIC_EXPLANATION_BASE = "topic-explanation.base";
  public static final String PRACTICE_TASK_BOOTSTRAP = "practice.task.bootstrap";
  public static final String PRACTICE_BASE_IDENTITY = "practice.base.identity";
  public static final String PRACTICE_COACH_GUIDED = "practice.strategy.coach-style.guided";
  public static final String PRACTICE_COACH_DIRECT = "practice.strategy.coach-style.direct";
  public static final String PRACTICE_COACH_FRAME = "practice.strategy.coach-style.frame";
  public static final String PRACTICE_RESPONSE_LANGUAGE = "practice.strategy.response-language";
  public static final String PRACTICE_INTERACTION = "practice.strategy.interaction";
  public static final String PRACTICE_CODE_REVIEW_TOOL_BOUNDARY = "practice.strategy.code-review-tool-boundary";
  public static final String PRACTICE_PROFILE_TOOL_BOUNDARY = "practice.strategy.profile-tool-boundary";
  public static final String PRACTICE_ACTIVE_SUMMARY_BOUNDARY = "practice.memory.active-summary-boundary";
  public static final String PRACTICE_LEARNER_PROFILE_BOUNDARY = "practice.memory.learner-profile-boundary";
  public static final String LEARNING_PLAN_DRAFT_BASE = "learning-plan-draft.base";
  public static final String LEARNING_PLAN_REVISION_BASE = "learning-plan-revision.base";
  public static final String LEARNING_PLAN_EXTENSION_BASE = "learning-plan-extension.base";
  public static final String PRACTICE_CODE_REVIEW_BASE = "practice-code-review.base";
  public static final String DECLARED_PROFILE_UPDATE_BASE = "learner-declared-profile-update.base";
  public static final String CODE_REVIEW_PROFILE_UPDATE_BASE = "code-review-profile-update.base";

  private SystemPromptSectionKeys() {
  }
}
