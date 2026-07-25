package org.congcong.algomentor.api.review.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.review.mapper.model.ProblemReviewAttemptRow;

@Mapper
public interface ProblemReviewAttemptMapper {

  ProblemReviewAttemptRow findByUserAndClientAttemptId(
      @Param("userId") long userId,
      @Param("clientAttemptId") String clientAttemptId
  );

  ProblemReviewAttemptRow insertIfAbsent(
      @Param("reviewCardId") long reviewCardId,
      @Param("userId") long userId,
      @Param("clientAttemptId") String clientAttemptId,
      @Param("rating") String rating,
      @Param("schedulingBeforeJson") JsonNode schedulingBeforeJson,
      @Param("schedulingAfterJson") JsonNode schedulingAfterJson,
      @Param("reviewedAt") Instant reviewedAt
  );

  List<ProblemReviewAttemptRow> findRecent(
      @Param("userId") long userId,
      @Param("reviewCardId") long reviewCardId,
      @Param("limit") int limit
  );
}
