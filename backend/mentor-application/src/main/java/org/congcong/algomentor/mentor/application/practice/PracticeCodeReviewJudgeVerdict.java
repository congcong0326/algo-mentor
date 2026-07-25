package org.congcong.algomentor.mentor.application.practice;

/** 正式代码 Review 对在线评测结果的静态或事实判定。 */
public enum PracticeCodeReviewJudgeVerdict {
  ACCEPTED(true, "已通过实际评测"),
  LIKELY_ACCEPTED(true, "静态分析预计可以通过评测"),
  WRONG_ANSWER(false, "存在错误答案风险"),
  TIME_LIMIT_EXCEEDED(false, "实际或预计会执行超时"),
  MEMORY_LIMIT_EXCEEDED(false, "实际或预计会内存超限"),
  COMPILE_ERROR(false, "代码无法通过编译"),
  RUNTIME_ERROR(false, "代码存在运行时错误"),
  UNKNOWN(false, "无法确认代码能够通过评测");

  private final boolean allowsPassing;
  private final String descriptionZh;

  PracticeCodeReviewJudgeVerdict(boolean allowsPassing, String descriptionZh) {
    this.allowsPassing = allowsPassing;
    this.descriptionZh = descriptionZh;
  }

  public boolean allowsPassing() {
    return allowsPassing;
  }

  public String descriptionZh() {
    return descriptionZh;
  }
}
