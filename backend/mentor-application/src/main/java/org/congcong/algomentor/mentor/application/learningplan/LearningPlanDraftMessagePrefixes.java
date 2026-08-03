package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

/** 前端继续草稿请求中用于表达“按新目标重新生成”的固定消息前缀。 */
public final class LearningPlanDraftMessagePrefixes {

  public static final String REGENERATE_ZH_CN = "请按新的目标摘要重新生成训练方案：";
  public static final String REGENERATE_ZH_CN_LEGACY = "请按新的目标摘要重新生成学习计划：";
  public static final String REGENERATE_EN_US = "Regenerate the learning plan from this updated objective summary:";
  public static final List<String> REGENERATE_PREFIXES = List.of(
      REGENERATE_ZH_CN,
      REGENERATE_ZH_CN_LEGACY,
      REGENERATE_EN_US);

  private LearningPlanDraftMessagePrefixes() {
  }
}
