package org.congcong.algomentor.api.learningplan.service;

public final class LearningPlanTemplateSeedConstants {

  /**
   * 学习计划模板 seed 目录中的模板 JSONL 文件名。
   */
  public static final String TEMPLATES_FILE = "learning_plan_templates.jsonl";

  /**
   * 学习计划模板 seed 目录中的题目引用 JSONL 文件名。
   */
  public static final String PROBLEM_REFS_FILE = "learning_plan_template_problem_refs.jsonl";

  /**
   * 学习计划模板 seed 导入 manifest 文件名。
   */
  public static final String MANIFEST_FILE = "learning_plan_template_seed_manifest.json";

  /**
   * 面向研发和产品的模板 seed 元数据说明文件名。
   */
  public static final String METADATA_FILE = "learning_plan_template_seed_metadata.md";

  /**
   * 学习计划模板 seed 导入审计 sourceName。
   */
  public static final String SOURCE_NAME = "learning-plan-template-seed";

  /**
   * 题目在同一模板内有意重复出现时，ref metadata 中记录原因的 key。
   */
  public static final String REPEAT_REASON_METADATA_KEY = "repeatReason";

  private LearningPlanTemplateSeedConstants() {
  }
}
