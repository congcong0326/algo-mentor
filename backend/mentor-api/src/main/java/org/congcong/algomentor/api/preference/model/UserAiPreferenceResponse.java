package org.congcong.algomentor.api.preference.model;

import java.time.Instant;

public record UserAiPreferenceResponse(
    String coachStyle,
    String coachStyleLabel,
    String responseLanguage,
    String responseLanguageLabel,
    Instant updatedAt
) {
}
