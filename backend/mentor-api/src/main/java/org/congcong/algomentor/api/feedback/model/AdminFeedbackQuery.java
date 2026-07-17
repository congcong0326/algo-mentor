package org.congcong.algomentor.api.feedback.model;

public record AdminFeedbackQuery(
    int page,
    int pageSize,
    FeedbackStatus status,
    FeedbackCategory category,
    Long userId,
    boolean unreadOnly
) {
}
