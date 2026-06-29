package org.congcong.algomentor.api.preference.mapper.model;

import java.time.Instant;

public record UserAiPreferenceRow(
    Long userId,
    String coachStyle,
    Instant createdAt,
    Instant updatedAt
) {
}
