package org.congcong.algomentor.api.feedback.service;

public record FeedbackReadResult(long threadId, int markedReadCount, long unreadMessageCount) {
}
