package org.congcong.algomentor.mentor.application.prompt;

import java.util.Objects;

/** 已合并的单个固定系统提示词 section，不向日志暴露正文。 */
public record ResolvedSystemPromptSection(
    String key,
    String text,
    ResolvedSystemPromptSectionSource source,
    String contentHash,
    int charCount
) {

  public ResolvedSystemPromptSection {
    key = requireText(key, "key");
    text = Objects.requireNonNull(text, "text must not be null");
    source = Objects.requireNonNull(source, "source must not be null");
    contentHash = requireText(contentHash, "contentHash");
    if (charCount != text.length()) {
      throw new IllegalArgumentException("charCount must match text length");
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
