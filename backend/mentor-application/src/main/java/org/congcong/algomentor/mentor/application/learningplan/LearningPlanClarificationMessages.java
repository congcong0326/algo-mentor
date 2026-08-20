package org.congcong.algomentor.mentor.application.learningplan;

/** 学习计划必填信息缺失时的稳定追问文案。 */
public final class LearningPlanClarificationMessages {

  private LearningPlanClarificationMessages() {
  }

  public static String forField(String field, LearningPlanContentLocale locale) {
    if (locale == LearningPlanContentLocale.EN_US) {
      return switch (field) {
        case "intent" -> "What kind of learning plan do you want, such as an interview sprint, topic breakthrough, or long-term learning plan?";
        case "objective" -> "What is the concrete objective for this plan?";
        case "targetProblemCount" -> "How many problems should this plan target: 5, 10, 15, 20, 25, or 30?";
        case "level" -> "Is your current algorithm level closer to beginner, intermediate, or advanced?";
        default -> "Please provide the most important missing detail so the plan can be generated.";
      };
    }
    return switch (field) {
      case "intent" -> "你想创建哪类学习计划？例如面试冲刺、专题突破或长期学习。";
      case "objective" -> "请补充这份计划的具体目标，例如准备 Java 后端算法面试。";
      case "targetProblemCount" -> "请选择计划题目规模：5、10、15、20、25 或 30 题。";
      case "level" -> "你当前算法水平更接近入门、中级还是高级？";
      default -> "请补充一个最关键的信息，方便继续生成计划。";
    };
  }
}
