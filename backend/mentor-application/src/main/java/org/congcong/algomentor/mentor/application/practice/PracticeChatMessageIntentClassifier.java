package org.congcong.algomentor.mentor.application.practice;

import java.util.Locale;

public final class PracticeChatMessageIntentClassifier {

  public static PracticeChatMessageIntent classify(String message) {
    String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
    if (containsAny(normalized, "完整代码", "直接给答案", "给答案", "完整题解", "java 解法", "java代码", "solution")) {
      return PracticeChatMessageIntent.ASK_SOLUTION;
    }
    if (containsAny(normalized, "wa", "tle", "runtime error", "compile error", "编译错误", "用例不通过", "答案错误")) {
      return PracticeChatMessageIntent.SUBMISSION_FEEDBACK;
    }
    if (normalized.contains("```")
        || normalized.contains("public class")
        || normalized.contains("class solution")
        || normalized.contains("def ")
        || normalized.contains("return ")) {
      return PracticeChatMessageIntent.CODE_DEBUG;
    }
    return PracticeChatMessageIntent.ASK_HINT;
  }

  private static boolean containsAny(String value, String... needles) {
    for (String needle : needles) {
      if (value.contains(needle)) {
        return true;
      }
    }
    return false;
  }

  private PracticeChatMessageIntentClassifier() {
  }
}
