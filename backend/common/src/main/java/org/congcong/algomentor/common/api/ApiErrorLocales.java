package org.congcong.algomentor.common.api;

import java.util.List;
import java.util.Locale;

/**
 * HTTP API 错误响应支持的本地化语言。
 */
public final class ApiErrorLocales {

  public static final Locale ZH_CN = Locale.forLanguageTag("zh-CN");
  public static final Locale EN_US = Locale.forLanguageTag("en-US");
  public static final Locale DEFAULT = ZH_CN;

  private static final List<Locale> SUPPORTED = List.of(ZH_CN, EN_US);

  private ApiErrorLocales() {
  }

  public static Locale resolve(Locale requestedLocale) {
    if (requestedLocale == null) {
      return DEFAULT;
    }
    for (Locale supportedLocale : SUPPORTED) {
      if (supportedLocale.toLanguageTag().equalsIgnoreCase(requestedLocale.toLanguageTag())) {
        return supportedLocale;
      }
    }
    return DEFAULT;
  }

  public static Locale parse(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return DEFAULT;
    }
    try {
      Locale matched = Locale.lookup(Locale.LanguageRange.parse(acceptLanguage), SUPPORTED);
      return resolve(matched);
    } catch (IllegalArgumentException exception) {
      return DEFAULT;
    }
  }
}
