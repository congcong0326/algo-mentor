package org.congcong.algomentor.api.review.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.review.mapper.model.ReviewPreferenceRow;

@Mapper
public interface ReviewPreferenceMapper {

  ReviewPreferenceRow findByUserId(@Param("userId") long userId);

  ReviewPreferenceRow upsert(
      @Param("userId") long userId,
      @Param("desiredRetention") BigDecimal desiredRetention,
      @Param("dailyNewLimit") int dailyNewLimit,
      @Param("dailyLearningLimit") int dailyLearningLimit,
      @Param("dailyReviewLimit") int dailyReviewLimit,
      @Param("maximumIntervalDays") int maximumIntervalDays,
      @Param("enableFuzzing") boolean enableFuzzing,
      @Param("createdAt") Instant createdAt,
      @Param("updatedAt") Instant updatedAt
  );
}
