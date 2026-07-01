package org.congcong.algomentor.mentor.application.learningplan.stream;

/**
 * 学习计划流式生成的事件名、schema 名和场景标识。
 */
public final class LearningPlanStreamConstants {

  /**
   * 学习计划用户工作状态场景。
   */
  public static final String SCENARIO = "learning_plan";

  /**
   * 学习计划草案结构化输出 schema 名。
   */
  public static final String SCHEMA_NAME = "learning_plan_draft";

  /**
   * 学习计划草案结构化输出 schema 版本。
   */
  public static final String SCHEMA_VERSION = "v1";

  /**
   * 草案已生成并保存。
   */
  public static final String DRAFT_READY = "draft_ready";

  /**
   * 草案业务生成失败。
   */
  public static final String DRAFT_ERROR = "draft_error";

  /**
   * 草案修订已生成并保存。
   */
  public static final String DRAFT_REVISION_READY = "draft_revision_ready";

  /**
   * 草案修订生成失败。
   */
  public static final String DRAFT_REVISION_ERROR = "draft_revision_error";

  /**
   * 学习计划延期方案已生成并保存。
   */
  public static final String PLAN_EXTENSION_READY = "plan_extension_ready";

  /**
   * 学习计划延期方案生成失败。
   */
  public static final String PLAN_EXTENSION_ERROR = "plan_extension_error";

  /**
   * 学习计划延期结构化输出 schema 名。
   */
  public static final String EXTENSION_SCHEMA_NAME = "learning_plan_extension";

  /**
   * 学习计划延期结构化输出 schema 版本。
   */
  public static final String EXTENSION_SCHEMA_VERSION = "v1";

  private LearningPlanStreamConstants() {
  }
}
