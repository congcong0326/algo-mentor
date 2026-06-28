package org.congcong.algomentor.mentor.application.preference;

import org.congcong.algomentor.mentor.application.practice.PracticeCoachStyle;
import org.congcong.algomentor.mentor.application.practice.PracticeResponseLanguage;

public record UserAiPreferenceUpdate(
    PracticeCoachStyle coachStyle,
    PracticeResponseLanguage responseLanguage
) {
}
