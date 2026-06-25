package org.congcong.algomentor.mentor.application.practice;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.model.AgentActiveRun;

public record PracticeSessionResult(
    PracticeSession session,
    PracticeChatProblemDetail problem,
    List<PracticeSessionMessage> messages,
    Optional<AgentActiveRun> activeRun,
    PracticeCodeReviewSummary latestReview,
    PracticeCompletionGate completionGate
) {

  public PracticeSessionResult(
      PracticeSession session,
      PracticeChatProblemDetail problem,
      List<PracticeSessionMessage> messages,
      Optional<AgentActiveRun> activeRun
  ) {
    this(session, problem, messages, activeRun, null, defaultCompletionGate());
  }

  public PracticeSessionResult {
    messages = messages == null ? List.of() : List.copyOf(messages);
    activeRun = activeRun == null ? Optional.empty() : activeRun;
    completionGate = completionGate == null ? defaultCompletionGate() : completionGate;
  }

  private static PracticeCompletionGate defaultCompletionGate() {
    return new PracticeCompletionGate(
        false,
        PracticeCompletionGate.ReasonCode.NO_REVIEW,
        "完成前需要先粘贴完整代码完成一次 AI Review。",
        Optional.empty(),
        PracticeCodeReviewConstants.PASS_SCORE);
  }
}
