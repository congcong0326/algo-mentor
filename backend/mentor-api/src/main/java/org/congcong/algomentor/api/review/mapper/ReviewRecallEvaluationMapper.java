package org.congcong.algomentor.api.review.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.review.mapper.model.ReviewRecallEvaluationRow;

@Mapper
public interface ReviewRecallEvaluationMapper {

  ReviewRecallEvaluationRow insert(
      @Param("mistakeNoteId") long mistakeNoteId,
      @Param("userId") long userId,
      @Param("recallText") String recallText,
      @Param("transientNote") String transientNote,
      @Param("suggestedRating") String suggestedRating,
      @Param("hitPointsJson") JsonNode hitPointsJson,
      @Param("missedPointsJson") JsonNode missedPointsJson,
      @Param("gapSummary") String gapSummary,
      @Param("aiSuggested") boolean aiSuggested,
      @Param("createdAt") Instant createdAt
  );

  ReviewRecallEvaluationRow findForUser(
      @Param("userId") long userId,
      @Param("evaluationId") long evaluationId
  );
}
