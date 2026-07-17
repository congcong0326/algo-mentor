package org.congcong.algomentor.api.feedback.model;

public record UserFeedbackQuery(int page, int pageSize, FeedbackStatus status) {
}
