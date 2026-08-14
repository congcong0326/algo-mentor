package org.congcong.algomentor.mentor.application.practice;

import java.util.Locale;

/** 学习计划语言显示值到 LeetCode starter code language slug 的稳定映射。 */
public final class PracticeCodeTemplateLanguage {

  private PracticeCodeTemplateLanguage() {
  }

  public static String slugFor(String programmingLanguage) {
    if (programmingLanguage == null || programmingLanguage.isBlank()) {
      return null;
    }
    return switch (programmingLanguage.trim().toLowerCase(Locale.ROOT)) {
      case "java" -> "java";
      case "python3" -> "python3";
      case "c++" -> "cpp";
      case "javascript" -> "javascript";
      case "typescript" -> "typescript";
      case "go" -> "golang";
      case "c#" -> "csharp";
      case "c" -> "c";
      case "kotlin" -> "kotlin";
      case "swift" -> "swift";
      case "rust" -> "rust";
      case "sql" -> "sql";
      default -> null;
    };
  }
}
