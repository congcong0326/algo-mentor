package org.congcong.algomentor.mentor.application.practice;

import java.util.Locale;

/**
 * 题目聊天回复语言白名单，用于在 prompt 中动态约束输出语言。
 */
public enum PracticeResponseLanguage {
  ZH_CN("zh-CN", "简体中文", "简体中文"),
  EN_US("en-US", "English", "英语");

  private final String languageTag;
  private final String label;
  private final String promptLabel;

  PracticeResponseLanguage(String languageTag, String label, String promptLabel) {
    this.languageTag = languageTag;
    this.label = label;
    this.promptLabel = promptLabel;
  }

  public String languageTag() {
    return languageTag;
  }

  public String label() {
    return label;
  }

  public String promptLabel() {
    return promptLabel;
  }

  public static PracticeResponseLanguage defaultLanguage() {
    return ZH_CN;
  }

  public static PracticeResponseLanguage fromLocale(String locale) {
    if (locale == null || locale.isBlank()) {
      return defaultLanguage();
    }
    String normalized = locale.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("en")) {
      return EN_US;
    }
    return ZH_CN;
  }

  public static PracticeResponseLanguage from(Object value) {
    if (value instanceof PracticeResponseLanguage language) {
      return language;
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return PracticeResponseLanguage.valueOf(text.trim());
      } catch (IllegalArgumentException ignored) {
        return defaultLanguage();
      }
    }
    return defaultLanguage();
  }
}
