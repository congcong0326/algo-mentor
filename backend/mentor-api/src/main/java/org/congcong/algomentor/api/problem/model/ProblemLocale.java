package org.congcong.algomentor.api.problem.model;

import java.util.Locale;

/**
 * 题库对外内容语言，兼容短 locale 入参并统一输出规范值。
 */
public enum ProblemLocale {
  ZH_CN("zh-CN"),
  EN_US("en-US");

  public static final ProblemLocale DEFAULT = ZH_CN;

  private final String value;

  ProblemLocale(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean isEnglish() {
    return this == EN_US;
  }

  public static ProblemLocale parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return DEFAULT;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "zh-cn", "zh" -> ZH_CN;
      case "en-us", "en" -> EN_US;
      default -> throw new UnsupportedProblemLocaleException(raw);
    };
  }

  public static class UnsupportedProblemLocaleException extends RuntimeException {
    public UnsupportedProblemLocaleException(String locale) {
      super("Unsupported problem locale: " + locale);
    }
  }
}
