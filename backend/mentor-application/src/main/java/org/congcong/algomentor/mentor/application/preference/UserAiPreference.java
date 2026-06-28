package org.congcong.algomentor.mentor.application.preference;

import java.time.Instant;
import org.congcong.algomentor.mentor.application.practice.PracticeCoachStyle;
import org.congcong.algomentor.mentor.application.practice.PracticeResponseLanguage;

public record UserAiPreference(
    long userId,
    PracticeCoachStyle coachStyle,
    PracticeResponseLanguage responseLanguage,
    Instant createdAt,
    Instant updatedAt
) {

  public UserAiPreference {
    coachStyle = coachStyle == null ? PracticeCoachStyle.defaultStyle() : coachStyle;
    responseLanguage = responseLanguage == null ? PracticeResponseLanguage.defaultLanguage() : responseLanguage;
  }
}
