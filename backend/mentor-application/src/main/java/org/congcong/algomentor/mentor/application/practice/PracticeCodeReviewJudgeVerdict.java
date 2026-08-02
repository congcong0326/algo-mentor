package org.congcong.algomentor.mentor.application.practice;

/** 正式代码 Review 对在线评测结果的静态或事实判定。 */
public enum PracticeCodeReviewJudgeVerdict {
  ACCEPTED(true, "已通过实际评测", "accepted by the actual judge"),
  LIKELY_ACCEPTED(true, "静态分析预计可以通过评测", "static analysis indicates the submission is likely accepted"),
  WRONG_ANSWER(false, "存在错误答案风险", "the submission produces an incorrect result"),
  TIME_LIMIT_EXCEEDED(false, "实际或预计会执行超时", "the submission exceeds or is expected to exceed the time limit"),
  MEMORY_LIMIT_EXCEEDED(false, "实际或预计会内存超限", "the submission exceeds or is expected to exceed the memory limit"),
  COMPILE_ERROR(false, "代码无法通过编译", "the submission does not compile"),
  RUNTIME_ERROR(false, "代码存在运行时错误", "the submission has a runtime error"),
  UNKNOWN(false, "无法确认代码能够通过评测", "there is insufficient evidence that the submission passes the judge");

  private final boolean allowsPassing;
  private final String descriptionZh;
  private final String descriptionEn;

  PracticeCodeReviewJudgeVerdict(boolean allowsPassing, String descriptionZh, String descriptionEn) {
    this.allowsPassing = allowsPassing;
    this.descriptionZh = descriptionZh;
    this.descriptionEn = descriptionEn;
  }

  public boolean allowsPassing() {
    return allowsPassing;
  }

  public String descriptionZh() {
    return descriptionZh;
  }

  public String description(PracticeResponseLanguage responseLanguage) {
    return responseLanguage == PracticeResponseLanguage.EN_US ? descriptionEn : descriptionZh;
  }
}
