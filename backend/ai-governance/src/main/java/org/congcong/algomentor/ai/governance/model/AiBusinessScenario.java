package org.congcong.algomentor.ai.governance.model;

import java.util.Arrays;
import java.util.Locale;

/**
 * 由代码注册的稳定 AI 业务场景目录。
 *
 * <p>场景 code 不携带系统提示词或模型路由策略的 schema 版本，供运行时路由、受管理提示词和管理端目录共同使用。</p>
 */
public enum AiBusinessScenario {
  PRACTICE_CHAT(
      "practice-chat", "PRACTICE", "题目训练聊天", "Practice chat",
      "题目训练聊天 Agent 使用的模型路由。", "Model routing for practice chat agents."),
  LEARNING_PLAN_DRAFT(
      "learning-plan-draft", "LEARNING_PLAN", "学习计划草案", "Learning plan draft",
      "学习计划草案使用的模型路由。", "Model routing for learning plan drafts."),
  LEARNING_PLAN_REVISION(
      "learning-plan-revision", "LEARNING_PLAN", "学习计划修订", "Learning plan revision",
      "学习计划修订使用的模型路由。", "Model routing for learning plan revisions."),
  LEARNING_PLAN_EXTENSION(
      "learning-plan-extension", "LEARNING_PLAN", "学习计划扩展", "Learning plan extension",
      "学习计划扩展使用的模型路由。", "Model routing for learning plan extensions."),
  PRACTICE_CODE_REVIEW(
      "practice-code-review", "PRACTICE", "练习代码 Review", "Practice code review",
      "练习代码 Review 使用的模型路由。", "Model routing for practice code reviews."),
  LEARNER_DECLARED_PROFILE_UPDATE(
      "learner-declared-profile-update", "LEARNER_PROFILE", "学习者自述画像更新",
      "Declared learner profile update", "学习者自述画像更新使用的模型路由。",
      "Model routing for declared learner profile updates."),
  CODE_REVIEW_PROFILE_UPDATE(
      "code-review-profile-update", "LEARNER_PROFILE", "Code Review 画像更新",
      "Code review profile update", "Code Review 画像更新使用的模型路由。",
      "Model routing for code review profile updates.");

  private final String code;
  private final String categoryCode;
  private final String displayNameZh;
  private final String displayNameEn;
  private final String descriptionZh;
  private final String descriptionEn;

  AiBusinessScenario(
      String code,
      String categoryCode,
      String displayNameZh,
      String displayNameEn,
      String descriptionZh,
      String descriptionEn
  ) {
    this.code = code;
    this.categoryCode = categoryCode;
    this.displayNameZh = displayNameZh;
    this.displayNameEn = displayNameEn;
    this.descriptionZh = descriptionZh;
    this.descriptionEn = descriptionEn;
  }

  public String code() {
    return code;
  }

  public String categoryCode() {
    return categoryCode;
  }

  public String displayNameZh() {
    return displayNameZh;
  }

  public String displayNameEn() {
    return displayNameEn;
  }

  public String descriptionZh() {
    return descriptionZh;
  }

  public String descriptionEn() {
    return descriptionEn;
  }

  public static AiBusinessScenario fromCode(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("AI business scenario code must not be blank");
    }
    String normalized = code.trim().toLowerCase(Locale.ROOT);
    return Arrays.stream(values())
        .filter(scenario -> scenario.code.equals(normalized))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown AI business scenario: " + code));
  }
}
