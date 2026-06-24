package org.congcong.algomentor.mentor.application.practice;

public record PracticeChatReference(
    long planId,
    int phaseIndex,
    String problemSlug,
    String locale
) {

  public PracticeChatReference {
    if (planId < 1) {
      throw new IllegalArgumentException("Practice chat plan id must be positive");
    }
    if (phaseIndex < 1) {
      throw new IllegalArgumentException("Practice chat phase index must be positive");
    }
    if (problemSlug == null || problemSlug.isBlank()) {
      throw new IllegalArgumentException("Practice chat problem slug must not be blank");
    }
    problemSlug = problemSlug.trim();
    locale = locale == null || locale.isBlank() ? "zh-CN" : locale.trim();
  }
}
