package org.congcong.algomentor.api.feedback.repository.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.feedback.model.AdminFeedbackQuery;
import org.congcong.algomentor.api.feedback.model.FeedbackCategory;
import org.congcong.algomentor.api.feedback.model.FeedbackMessage;
import org.congcong.algomentor.api.feedback.model.FeedbackOverviewStats;
import org.congcong.algomentor.api.feedback.model.FeedbackSenderType;
import org.congcong.algomentor.api.feedback.model.FeedbackStatus;
import org.congcong.algomentor.api.feedback.model.FeedbackThread;
import org.congcong.algomentor.api.feedback.model.FeedbackThreadSummary;
import org.congcong.algomentor.api.feedback.model.UserFeedbackQuery;
import org.congcong.algomentor.api.feedback.repository.FeedbackRepository;

public class MyBatisFeedbackRepository implements FeedbackRepository {
  private final FeedbackMapper mapper;

  public MyBatisFeedbackRepository(FeedbackMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long insertThread(long userId, FeedbackCategory category, String subject, String sourcePath,
      String sourceRequestId, String sourceRunId, Instant now) {
    return requireId(mapper.insertThread(userId, category, subject, sourcePath, sourceRequestId, sourceRunId, now));
  }

  @Override
  public long insertMessage(long threadId, FeedbackSenderType senderType, long senderUserId, String content, Instant now) {
    return requireId(mapper.insertMessage(threadId, senderType, senderUserId, content, now));
  }

  @Override public Optional<FeedbackThread> findThreadById(long threadId) { return Optional.ofNullable(mapper.findThreadById(threadId)).map(row -> row.toDomain()); }
  @Override public Optional<FeedbackThread> findThreadByIdForUpdate(long threadId) { return Optional.ofNullable(mapper.findThreadByIdForUpdate(threadId)).map(row -> row.toDomain()); }
  @Override public List<FeedbackThreadSummary> findUserThreadPage(long userId, UserFeedbackQuery query, int offset) { return mapper.findUserThreadPage(userId, query, query.pageSize(), offset).stream().map(row -> row.toDomain()).toList(); }
  @Override public long countUserThreads(long userId, UserFeedbackQuery query) { return mapper.countUserThreads(userId, query); }
  @Override public List<FeedbackThreadSummary> findAdminThreadPage(AdminFeedbackQuery query, int offset) { return mapper.findAdminThreadPage(query, query.pageSize(), offset).stream().map(row -> row.toDomain()).toList(); }
  @Override public long countAdminThreads(AdminFeedbackQuery query) { return mapper.countAdminThreads(query); }
  @Override public List<FeedbackMessage> findMessagesByThreadId(long threadId) { return mapper.findMessagesByThreadId(threadId).stream().map(row -> row.toDomain()).toList(); }
  @Override public long countUnreadMessagesForUser(long userId) { return mapper.countUnreadMessagesForUser(userId); }
  @Override public long countUnreadMessagesForAdmin() { return mapper.countUnreadMessagesForAdmin(); }
  @Override public int markAdminMessagesReadByUser(long threadId, long userId, Instant readAt) { return mapper.markAdminMessagesReadByUser(threadId, userId, readAt); }
  @Override public int markUserMessagesReadByAdmin(long threadId, Instant readAt) { return mapper.markUserMessagesReadByAdmin(threadId, readAt); }
  @Override public void updateThreadActivity(long threadId, Instant now) { mapper.updateThreadActivity(threadId, now); }
  @Override public void reopenThreadAfterUserReply(long threadId, Instant now) { mapper.reopenThreadAfterUserReply(threadId, now); }
  @Override public void closeThread(long threadId, long closedBy, Instant now) { mapper.closeThread(threadId, closedBy, now); }
  @Override public void reopenThread(long threadId, Instant now) { mapper.reopenThread(threadId, now); }
  @Override public FeedbackOverviewStats feedbackOverviewStats() { return new FeedbackOverviewStats(mapper.countOpenThreads(), mapper.countUnreadMessagesForAdmin()); }

  private long requireId(Long id) {
    if (id == null || id < 1) {
      throw new IllegalStateException("Feedback write did not return an identifier.");
    }
    return id;
  }
}
