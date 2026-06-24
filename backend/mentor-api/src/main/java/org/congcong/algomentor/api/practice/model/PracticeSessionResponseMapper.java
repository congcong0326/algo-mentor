package org.congcong.algomentor.api.practice.model;

import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemDetail;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionMessage;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionResult;

public final class PracticeSessionResponseMapper {

  private PracticeSessionResponseMapper() {
  }

  public static PracticeSessionResponse toResponse(PracticeSessionResult result) {
    return new PracticeSessionResponse(
        toSession(result.session()),
        toProblem(result.problem()),
        result.messages().stream()
            .map(PracticeSessionResponseMapper::toMessage)
            .toList());
  }

  private static PracticeSessionSummaryResponse toSession(PracticeSession session) {
    if (session.agentTaskId() == null) {
      throw new IllegalStateException("Practice session agent task id is not attached. sessionId=" + session.id());
    }
    return new PracticeSessionSummaryResponse(
        session.id(),
        session.planId(),
        session.phaseIndex(),
        session.problemSlug(),
        session.progressStatus().name(),
        session.agentTaskId(),
        session.createdAt(),
        session.updatedAt());
  }

  private static PracticeProblemSummaryResponse toProblem(PracticeChatProblemDetail problem) {
    return new PracticeProblemSummaryResponse(
        problem.slug(),
        problem.frontendId(),
        problem.title(),
        null,
        problem.difficulty(),
        problem.tags(),
        problem.leetcodeUrl());
  }

  private static PracticeMessageResponse toMessage(PracticeSessionMessage message) {
    return new PracticeMessageResponse(
        message.id(),
        message.role(),
        message.messageType(),
        message.contentMarkdown(),
        message.createdAt());
  }
}
