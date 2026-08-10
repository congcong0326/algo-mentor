package org.congcong.algomentor.mentor.application.practice;

import java.time.Instant;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryMessageAction;

public record PracticeSessionMessage(
    long id,
    String role,
    String messageType,
    String contentMarkdown,
    Instant createdAt,
    CoachSummaryMessageAction coachSummaryAction
) {

  public PracticeSessionMessage(
      long id,
      String role,
      String messageType,
      String contentMarkdown,
      Instant createdAt
  ) {
    this(id, role, messageType, contentMarkdown, createdAt, null);
  }
}
