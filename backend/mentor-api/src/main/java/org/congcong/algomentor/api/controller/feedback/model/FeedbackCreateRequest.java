package org.congcong.algomentor.api.controller.feedback.model;
public record FeedbackCreateRequest(String category, String subject, String content, String sourcePath, String sourceRequestId, String sourceRunId) { }
