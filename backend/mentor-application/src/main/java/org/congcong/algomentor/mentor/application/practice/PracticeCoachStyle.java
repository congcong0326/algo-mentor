package org.congcong.algomentor.mentor.application.practice;

/**
 * 题目聊天教练风格白名单。用户只能选择枚举，不能注入任意 system prompt。
 */
public enum PracticeCoachStyle {
  SOCRATIC_GUIDE(
      "启发型教练",
      "Act as a patient Socratic algorithm coach. Lead with hints, key observations, state definitions, boundary cases, and small questions before giving a full solution."),
  DIRECT_EXPLAINER(
      "直给型教练",
      "Act as a concise direct explainer. Prefer complete reasoning, complexity, pitfalls, and runnable code when the learner asks for help."),
  INTERVIEWER(
      "面试官教练",
      "Act like an algorithm interviewer. Ask about constraints, complexity, edge cases, alternative approaches, and trade-offs before accepting an answer."),
  STRICT_REVIEWER(
      "严苛 Review 官",
      "Act as a strict code reviewer. Focus on correctness risks, counterexamples, complexity regressions, edge cases, and implementation quality."),
  SUPPORTIVE_MENTOR(
      "陪伴型教练",
      "Act as a supportive mentor. Break ideas into smaller steps, reduce frustration, and keep a calm tone while preserving correctness standards.");

  private final String label;
  private final String instruction;

  PracticeCoachStyle(String label, String instruction) {
    this.label = label;
    this.instruction = instruction;
  }

  public String label() {
    return label;
  }

  public String instruction() {
    return instruction;
  }

  public static PracticeCoachStyle defaultStyle() {
    return SOCRATIC_GUIDE;
  }

  public static PracticeCoachStyle from(Object value) {
    if (value instanceof PracticeCoachStyle style) {
      return style;
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return PracticeCoachStyle.valueOf(text.trim());
      } catch (IllegalArgumentException ignored) {
        return defaultStyle();
      }
    }
    return defaultStyle();
  }
}
