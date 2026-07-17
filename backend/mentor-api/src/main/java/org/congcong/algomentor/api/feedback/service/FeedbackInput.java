package org.congcong.algomentor.api.feedback.service;

import org.congcong.algomentor.api.feedback.model.FeedbackCategory;

public record FeedbackInput(
    FeedbackCategory category,
    String subject,
    String content,
    String sourcePath,
    String sourceRequestId,
    String sourceRunId
) {
}
