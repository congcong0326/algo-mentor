package org.congcong.algomentor.mentor.application.review;

public interface ReviewLogRepository {

  void append(ReviewLogEntry entry);
}
