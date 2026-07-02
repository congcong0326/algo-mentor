package org.congcong.algomentor.api.review.repository;

import org.congcong.algomentor.api.review.mapper.ReviewLogMapper;
import org.congcong.algomentor.api.review.mapper.model.ReviewLogInsertRow;
import org.congcong.algomentor.mentor.application.review.ReviewLogEntry;
import org.congcong.algomentor.mentor.application.review.ReviewLogRepository;
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
        entry.grade().q(),
        entry.gradeSource().name(),
        entry.aiJudgmentJson(),
        entry.practiceCodeReviewId(),
        entry.recallMessageId(),
        entry.intervalBefore(),
        entry.intervalAfter(),
        entry.easeFactorBefore(),
        entry.easeFactorAfter(),
        entry.reviewedAt()));
  }
}
