package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

public record PracticeChatProblemDetail(
    String slug,
    Integer frontendId,
    String title,
    String difficulty,
    List<String> tags,
    String contentMarkdown,
    String leetcodeUrl
) {

  public PracticeChatProblemDetail {
    tags = tags == null ? List.of() : List.copyOf(tags);
  }
}
