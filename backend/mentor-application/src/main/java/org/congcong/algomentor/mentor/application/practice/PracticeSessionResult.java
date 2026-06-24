package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

public record PracticeSessionResult(
    PracticeSession session,
    PracticeChatProblemDetail problem,
    List<PracticeSessionMessage> messages
) {

  public PracticeSessionResult {
    messages = messages == null ? List.of() : List.copyOf(messages);
  }
}
