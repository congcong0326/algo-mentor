package org.congcong.algomentor.mentor.application.profile;

import java.util.List;
import java.util.regex.Pattern;

/** 对画像正文执行最小的长度与明显密钥泄露检查。 */
public final class LearnerProfileContentPolicy {

  private static final List<Pattern> SECRET_PATTERNS = List.of(
      Pattern.compile("(?i)\\bsk-[a-z0-9]{16,}\\b"),
      Pattern.compile("(?i)\\b(?:api[_-]?key|authorization|bearer)\\s*[:=]\\s*[^\\s]{8,}"));

  private final int maxChars;

  public LearnerProfileContentPolicy(int maxChars) {
    if (maxChars < 1) {
      throw new IllegalArgumentException("Learner profile content max chars must be positive");
    }
    this.maxChars = maxChars;
  }

  public String validateAndNormalize(String contentText) {
    if (contentText == null || contentText.isBlank()) {
      throw new IllegalArgumentException("Learner profile content must not be blank");
    }
    String normalized = contentText.trim();
    if (normalized.length() > maxChars) {
      throw new IllegalArgumentException("Learner profile content exceeds configured maximum length");
    }
    if (SECRET_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find())) {
      throw new IllegalArgumentException("Learner profile content contains a secret-like value");
    }
    return normalized;
  }
}
