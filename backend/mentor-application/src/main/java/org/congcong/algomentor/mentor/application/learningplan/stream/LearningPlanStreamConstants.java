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

  private LearningPlanStreamConstants() {
  }
}
