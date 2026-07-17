package org.congcong.algomentor.api.feedback.model;

/** 管理员概览使用的反馈待办统计，不含任何消息正文。 */
public record FeedbackOverviewStats(long openThreadCount, long adminUnreadMessageCount) {
}
