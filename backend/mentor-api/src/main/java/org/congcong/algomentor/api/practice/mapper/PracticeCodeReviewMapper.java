package org.congcong.algomentor.api.practice.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewInsertRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewSessionLockRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewSummaryRow;

@Mapper
public interface PracticeCodeReviewMapper {

  PracticeCodeReviewSessionLockRow lockSessionForReviewInsert(
      @Param("userId") long userId,
      @Param("sessionId") long sessionId
  );

  PracticeCodeReviewRow insert(PracticeCodeReviewInsertRow row);

  PracticeCodeReviewRow findLatest(@Param("userId") long userId, @Param("sessionId") long sessionId);

  PracticeCodeReviewSummaryRow findLatestSummary(@Param("userId") long userId, @Param("sessionId") long sessionId);

  List<PracticeCodeReviewSummaryRow> findSummaries(@Param("userId") long userId, @Param("sessionId") long sessionId);

  PracticeCodeReviewRow findById(
      @Param("userId") long userId,
      @Param("sessionId") long sessionId,
      @Param("reviewId") long reviewId
  );

  PracticeCodeReviewRow findByUserMessage(
      @Param("userId") long userId,
      @Param("sessionId") long sessionId,
      @Param("userMessageId") long userMessageId
  );
}
