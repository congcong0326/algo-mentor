package org.congcong.algomentor.mentor.application.preference;

import java.time.Instant;
import org.congcong.algomentor.mentor.application.practice.PracticeCoachStyle;

public record UserAiPreference(
    long userId,
    PracticeCoachStyle coachStyle,
    Instant createdAt,
    Instant updatedAt
) {

  public UserAiPreference {
    coachStyle = coachStyle == null ? PracticeCoachStyle.defaultStyle() : coachStyle;
  }
}
