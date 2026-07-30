package org.congcong.algomentor.mentor.application.profile.tool;

/** 用户自述画像 Agent 工具的稳定名称、JSON 字段和提示词版本契约。 */
public final class LearnerDeclaredProfileToolContracts {

  public static final String TOOL_NAME = "update_learner_declared_profile";

  public static final String ARGUMENT_UPDATES = "updates";
  public static final String UPDATE_DIMENSION = "dimension";
  public static final String UPDATE_STATEMENT = "statement";
  public static final String UPDATE_INTENT = "intent";

  public static final String RESULT_TYPE = "learner_declared_profile_update";
  public static final String RESULT_FIELD_TYPE = "type";
  public static final String RESULT_FIELD_STATUS = "status";
  public static final String RESULT_FIELD_MESSAGE = "message";
  public static final String RESULT_FIELD_ITEMS = "items";
  public static final String RESULT_ITEM_DIMENSION = "dimension";
  public static final String RESULT_ITEM_STATUS = "status";
  public static final String RESULT_ITEM_CONTENT_SUMMARY = "contentSummary";

  public static final String PROMPT_VERSION = "learner-declared-profile-update-v1";
  public static final String METADATA_DIMENSION_COUNT = "learnerProfileDimensionCount";
  public static final String AGENT_TITLE = "learner-declared-profile-update";
  public static final String CHILD_IDEMPOTENCY_KEY_PREFIX = "learner-declared-profile:";
  public static final String CHILD_RETRY_IDEMPOTENCY_KEY_SEPARATOR = ":retry:";
  public static final String SCHEMA_VERSION = "v1";

  public static final int MAX_STATEMENT_CHARS = 4_000;

  public static final String MESSAGE_UPDATED = "已同步长期学习者自述。";
  public static final String MESSAGE_NO_CHANGE = "现有学习者画像无需更新。";
  public static final String MESSAGE_FAILED = "本次学习者画像未更新。";

  private LearnerDeclaredProfileToolContracts() {
  }
}
