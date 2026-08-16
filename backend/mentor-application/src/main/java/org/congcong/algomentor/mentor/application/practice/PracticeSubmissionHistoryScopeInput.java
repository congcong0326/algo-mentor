package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

/** Prompt 历史题索引对应的服务端 capability 输入，不参与 Prompt 渲染。 */
public record PracticeSubmissionHistoryScopeInput(
    String problemRef,
    String problemSlug,
    String title,
    List<String> tags
) {

  public PracticeSubmissionHistoryScopeInput {
    if (problemRef == null || problemRef.isBlank() || problemSlug == null || problemSlug.isBlank()
        || title == null || title.isBlank()) {
      throw new IllegalArgumentException("Practice submission history scope input is invalid");
    }
    problemRef = problemRef.trim();
    problemSlug = problemSlug.trim();
    title = title.trim();
    tags = tags == null ? List.of() : tags.stream()
        .filter(tag -> tag != null && !tag.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
  }
}
