package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

public record PracticeChatProblemDetail(
    String slug,
    Integer frontendId,
    String title,
    String titleCn,
    String difficulty,
    List<String> tags,
    String contentMarkdown,
    String leetcodeUrl,
    List<PracticeCodeTemplate> codeTemplates
) {

  public PracticeChatProblemDetail(
      String slug,
      Integer frontendId,
      String title,
      String titleCn,
      String difficulty,
      List<String> tags,
      String contentMarkdown,
      String leetcodeUrl
  ) {
    this(slug, frontendId, title, titleCn, difficulty, tags, contentMarkdown, leetcodeUrl, List.of());
  }

  public PracticeChatProblemDetail {
    tags = tags == null ? List.of() : List.copyOf(tags);
    codeTemplates = codeTemplates == null ? List.of() : List.copyOf(codeTemplates);
  }

  public PracticeCodeTemplate templateFor(String programmingLanguage) {
    String languageSlug = PracticeCodeTemplateLanguage.slugFor(programmingLanguage);
    if (languageSlug == null) {
      return null;
    }
    return codeTemplates.stream()
        .filter(template -> languageSlug.equalsIgnoreCase(template.languageSlug()))
        .findFirst()
        .orElse(null);
  }
}
