package org.congcong.algomentor.api.practice.model;

import java.time.Instant;

public record PracticeMessageResponse(
    long id,
    String role,
    String messageType,
    String contentMarkdown,
    Instant createdAt) {
}
