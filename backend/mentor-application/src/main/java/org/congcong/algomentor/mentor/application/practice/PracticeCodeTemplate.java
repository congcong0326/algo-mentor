package org.congcong.algomentor.mentor.application.practice;

/** 当前题目某一编程语言的 starter code。 */
public record PracticeCodeTemplate(
    String languageSlug,
    String languageLabel,
    String code
) {

  public PracticeCodeTemplate {
    if (languageSlug == null || languageSlug.isBlank()) {
      throw new IllegalArgumentException("Practice code template language slug must not be blank");
    }
    if (languageLabel == null || languageLabel.isBlank()) {
      throw new IllegalArgumentException("Practice code template language label must not be blank");
    }
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Practice code template code must not be blank");
    }
    languageSlug = languageSlug.trim();
    languageLabel = languageLabel.trim();
  }
}
