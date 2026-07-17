package org.congcong.algomentor.api.feedback.repository;

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

/** 反馈表 SQL 的唯一应用层访问端口。 */
public interface FeedbackRepository {

  long insertThread(long userId, FeedbackCategory category, String subject, String sourcePath,
      String sourceRequestId, String sourceRunId, Instant now);

  long insertMessage(long threadId, FeedbackSenderType senderType, long senderUserId, String content, Instant now);

  Optional<FeedbackThread> findThreadById(long threadId);

  Optional<FeedbackThread> findThreadByIdForUpdate(long threadId);

  List<FeedbackThreadSummary> findUserThreadPage(long userId, UserFeedbackQuery query, int offset);

  long countUserThreads(long userId, UserFeedbackQuery query);

  List<FeedbackThreadSummary> findAdminThreadPage(AdminFeedbackQuery query, int offset);

  long countAdminThreads(AdminFeedbackQuery query);

  List<FeedbackMessage> findMessagesByThreadId(long threadId);

  long countUnreadMessagesForUser(long userId);

  long countUnreadMessagesForAdmin();

  int markAdminMessagesReadByUser(long threadId, long userId, Instant readAt);

  int markUserMessagesReadByAdmin(long threadId, Instant readAt);

  void updateThreadActivity(long threadId, Instant now);

  void reopenThreadAfterUserReply(long threadId, Instant now);

  void closeThread(long threadId, long closedBy, Instant now);

  void reopenThread(long threadId, Instant now);

  FeedbackOverviewStats feedbackOverviewStats();
}
