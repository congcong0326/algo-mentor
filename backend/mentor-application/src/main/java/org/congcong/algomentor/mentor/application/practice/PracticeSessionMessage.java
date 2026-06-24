package org.congcong.algomentor.mentor.application.practice;

import java.time.Instant;

public record PracticeSessionMessage(
    long id,
    String role,
    String messageType,
    String contentMarkdown,
    Instant createdAt
) {
}
