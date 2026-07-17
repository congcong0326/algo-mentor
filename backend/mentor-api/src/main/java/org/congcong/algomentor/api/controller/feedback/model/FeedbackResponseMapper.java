package org.congcong.algomentor.api.controller.feedback.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.api.feedback.model.FeedbackMessage;
import org.congcong.algomentor.api.feedback.model.FeedbackThreadPage;
import org.congcong.algomentor.api.feedback.model.FeedbackThreadSummary;
import org.congcong.algomentor.api.feedback.service.AdminFeedbackService.FeedbackUserSummary;
import org.congcong.algomentor.api.feedback.service.FeedbackReadResult;
import org.congcong.algomentor.api.feedback.service.FeedbackThreadDetail;

public final class FeedbackResponseMapper {
  private FeedbackResponseMapper() { }
  public static FeedbackThreadDetailResponse detail(FeedbackThreadDetail detail) {
    var thread = detail.thread();
    return new FeedbackThreadDetailResponse(thread.id(), thread.category().name(), thread.status().name(), thread.subject(),
        thread.sourcePath(), thread.sourceRequestId(), thread.sourceRunId(), thread.createdAt(), thread.updatedAt(),
        thread.closedAt(), thread.closedBy(), detail.unreadMessageCount(), detail.messages().stream().map(FeedbackResponseMapper::message).toList());
  }
  public static FeedbackThreadPageResponse page(FeedbackThreadPage page, Map<Long, FeedbackUserSummary> users) {
    return new FeedbackThreadPageResponse(page.items().stream().map(item -> summary(item, users == null ? null : users.get(item.userId()))).toList(),
        page.total(), page.page(), page.pageSize(), page.unreadMessageCount());
  }
  public static FeedbackReadResponse read(FeedbackReadResult result) { return new FeedbackReadResponse(result.threadId(), result.markedReadCount(), result.unreadMessageCount()); }
  private static FeedbackThreadSummaryResponse summary(FeedbackThreadSummary item, FeedbackUserSummary user) {
    return new FeedbackThreadSummaryResponse(item.id(), item.category().name(), item.status().name(), item.subject(),
        item.lastSenderType().name(), item.unreadMessageCount(), item.sourceRunId(), item.createdAt(), item.updatedAt(), item.closedAt(),
        user == null ? null : new FeedbackUserResponse(user.id(), user.email(), user.displayName(), user.status()));
  }
  private static FeedbackMessageResponse message(FeedbackMessage message) { return new FeedbackMessageResponse(message.id(), message.senderType().name(), message.senderUserId(), message.content(), message.readAt(), message.createdAt()); }
  public record FeedbackThreadPageResponse(List<FeedbackThreadSummaryResponse> items, long total, int page, int pageSize, long unreadMessageCount) { }
  public record FeedbackThreadSummaryResponse(long id, String category, String status, String subject, String lastSenderType, long unreadMessageCount, String sourceRunId, Instant createdAt, Instant updatedAt, Instant closedAt, FeedbackUserResponse user) { }
  public record FeedbackThreadDetailResponse(long id, String category, String status, String subject, String sourcePath, String sourceRequestId, String sourceRunId, Instant createdAt, Instant updatedAt, Instant closedAt, Long closedBy, long unreadMessageCount, List<FeedbackMessageResponse> messages) { }
  public record FeedbackMessageResponse(long id, String senderType, long senderUserId, String content, Instant readAt, Instant createdAt) { }
  public record FeedbackUserResponse(long id, String email, String displayName, String status) { }
  public record FeedbackReadResponse(long threadId, int markedReadCount, long unreadMessageCount) { }
}
