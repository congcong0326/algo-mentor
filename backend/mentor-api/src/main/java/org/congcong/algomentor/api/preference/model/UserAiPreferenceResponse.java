package org.congcong.algomentor.api.preference.model;

import java.time.Instant;

public record UserAiPreferenceResponse(
    String coachStyle,
    String coachStyleLabel,
    Instant updatedAt
) {
}
