package org.congcong.algomentor.common.api;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 从资源文件解析 HTTP API 错误消息。
 */
public class ApiErrorMessageResolver {

  public static final String BUNDLE_BASE_NAME = "i18n.api-errors";

  public String message(String code, String fallbackMessage, Locale locale, Object... args) {
    return message(code, null, fallbackMessage, locale, args);
  }

  public String message(String code, String messageKey, String fallbackMessage, Locale locale, Object... args) {
    String key = messageKey == null || messageKey.isBlank() ? ApiErrorMessageKeys.defaultKey(code) : messageKey;
    Locale resolvedLocale = ApiErrorLocales.resolve(locale);
    try {
      String pattern = ResourceBundle.getBundle(BUNDLE_BASE_NAME, resolvedLocale).getString(key);
      return new MessageFormat(pattern, resolvedLocale).format(args == null ? new Object[]{} : args);
    } catch (MissingResourceException exception) {
      return fallbackMessage;
    }
  }
}
