package org.congcong.algomentor.auth.betaaccess.service;

import java.util.Locale;

/**
 * 内测准入统一使用的邮箱规范化与基础格式校验。
 */
public final class BetaEmailAddress {

  public static final int MAX_LENGTH = 320;

  private BetaEmailAddress() {
  }

  public static String normalize(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  public static boolean isValid(String email) {
    String normalized = normalize(email);
    if (normalized.isEmpty() || normalized.length() > MAX_LENGTH) {
      return false;
    }
    if (normalized.chars().anyMatch(Character::isWhitespace)) {
      return false;
    }
    int at = normalized.indexOf('@');
    return at > 0
        && at == normalized.lastIndexOf('@')
        && at < normalized.length() - 1;
  }
}
