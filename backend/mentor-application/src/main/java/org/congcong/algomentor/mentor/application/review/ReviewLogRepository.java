package org.congcong.algomentor.mentor.application.review;

import java.util.List;

public interface ReviewLogRepository {

  void append(ReviewLogEntry entry);

  List<ReviewRecallHistoryItem> findRecentRecallHistory(long userId, long noteId, int limit);
}
