package org.congcong.algomentor.api.review.repository;

import java.util.List;
import org.congcong.algomentor.api.review.mapper.ReviewLogMapper;
import org.congcong.algomentor.api.review.mapper.model.ReviewLogInsertRow;
import org.congcong.algomentor.mentor.application.review.ReviewLogEntry;
import org.congcong.algomentor.mentor.application.review.ReviewLogRepository;
import org.congcong.algomentor.mentor.application.review.ReviewRating;
import org.congcong.algomentor.mentor.application.review.ReviewRecallHistoryItem;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisReviewLogRepository implements ReviewLogRepository {

  private final ReviewLogMapper mapper;

  public MyBatisReviewLogRepository(ReviewLogMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public void append(ReviewLogEntry entry) {
    mapper.insert(new ReviewLogInsertRow(
        entry.mistakeNoteId(),
        entry.userId(),
        entry.reviewMode().name(),
        entry.cardVariant() == null ? null : entry.cardVariant().name(),
        entry.cardPromptJson(),
        entry.userRecallText(),
        entry.userNoteTransient(),
        entry.rating() == null ? null : entry.rating().name(),
        entry.ratingSource().name(),
        entry.aiJudgmentJson(),
        entry.practiceCodeReviewId(),
        entry.recallMessageId(),
        entry.intervalBefore(),
        entry.intervalAfter(),
        entry.reviewedAt()));
  }

  @Override
  @Transactional(readOnly = true)
  public List<ReviewRecallHistoryItem> findRecentRecallHistory(long userId, long noteId, int limit) {
    int effectiveLimit = limit <= 0 ? 5 : Math.min(limit, 20);
    return mapper.findRecentRecallHistory(userId, noteId, effectiveLimit).stream()
        .map(row -> new ReviewRecallHistoryItem(
            row.id(),
            row.rating() == null ? ReviewRating.HARD : ReviewRating.valueOf(row.rating()),
            row.userRecallText(),
            row.userNoteTransient(),
            row.reviewedAt(),
            row.intervalAfter()))
        .toList();
  }
}
