package org.congcong.algomentor.mentor.application.practice;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.model.AgentActiveRun;

public record PracticeSessionResult(
    PracticeSession session,
    PracticeChatProblemDetail problem,
    List<PracticeSessionMessage> messages,
    Optional<AgentActiveRun> activeRun
) {

  public PracticeSessionResult {
    messages = messages == null ? List.of() : List.copyOf(messages);
    activeRun = activeRun == null ? Optional.empty() : activeRun;
  }
}
