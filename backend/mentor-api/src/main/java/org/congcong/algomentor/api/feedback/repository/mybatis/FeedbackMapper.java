package org.congcong.algomentor.api.feedback.repository.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.feedback.model.AdminFeedbackQuery;
import org.congcong.algomentor.api.feedback.model.FeedbackCategory;
import org.congcong.algomentor.api.feedback.model.FeedbackSenderType;
import org.congcong.algomentor.api.feedback.model.UserFeedbackQuery;
import org.congcong.algomentor.api.feedback.repository.mybatis.model.FeedbackMessageRow;
import org.congcong.algomentor.api.feedback.repository.mybatis.model.FeedbackThreadRow;
import org.congcong.algomentor.api.feedback.repository.mybatis.model.FeedbackThreadSummaryRow;

public interface FeedbackMapper {
  Long insertThread(@Param("userId") long userId, @Param("category") FeedbackCategory category,
      @Param("subject") String subject, @Param("sourcePath") String sourcePath,
      @Param("sourceRequestId") String sourceRequestId, @Param("sourceRunId") String sourceRunId,
      @Param("now") Instant now);
  Long insertMessage(@Param("threadId") long threadId, @Param("senderType") FeedbackSenderType senderType,
      @Param("senderUserId") long senderUserId, @Param("content") String content, @Param("now") Instant now);
  FeedbackThreadRow findThreadById(@Param("threadId") long threadId);
  FeedbackThreadRow findThreadByIdForUpdate(@Param("threadId") long threadId);
  List<FeedbackThreadSummaryRow> findUserThreadPage(@Param("userId") long userId,
      @Param("query") UserFeedbackQuery query, @Param("limit") int limit, @Param("offset") int offset);
  long countUserThreads(@Param("userId") long userId, @Param("query") UserFeedbackQuery query);
  List<FeedbackThreadSummaryRow> findAdminThreadPage(@Param("query") AdminFeedbackQuery query,
      @Param("limit") int limit, @Param("offset") int offset);
  long countAdminThreads(@Param("query") AdminFeedbackQuery query);
  List<FeedbackMessageRow> findMessagesByThreadId(@Param("threadId") long threadId);
  long countUnreadMessagesForUser(@Param("userId") long userId);
  long countUnreadMessagesForAdmin();
  int markAdminMessagesReadByUser(@Param("threadId") long threadId, @Param("userId") long userId,
      @Param("readAt") Instant readAt);
  int markUserMessagesReadByAdmin(@Param("threadId") long threadId, @Param("readAt") Instant readAt);
  int updateThreadActivity(@Param("threadId") long threadId, @Param("now") Instant now);
  int reopenThreadAfterUserReply(@Param("threadId") long threadId, @Param("now") Instant now);
  int closeThread(@Param("threadId") long threadId, @Param("closedBy") long closedBy, @Param("now") Instant now);
  int reopenThread(@Param("threadId") long threadId, @Param("now") Instant now);
  long countOpenThreads();
}
