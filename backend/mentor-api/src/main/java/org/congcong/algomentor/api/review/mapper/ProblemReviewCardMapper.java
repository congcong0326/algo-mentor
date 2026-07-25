package org.congcong.algomentor.api.review.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.review.mapper.model.ProblemReviewCardRow;
import org.congcong.algomentor.api.review.mapper.model.ProblemReviewCardUpsertRow;

@Mapper
public interface ProblemReviewCardMapper {

  ProblemReviewCardRow upsertForReview(ProblemReviewCardUpsertRow row);

  ProblemReviewCardRow mark(
      @Param("userId") long userId,
      @Param("problemSlug") String problemSlug,
      @Param("source") String source,
      @Param("sourceDetailJson") JsonNode sourceDetailJson,
      @Param("now") Instant now
  );

  ProblemReviewCardRow findForUser(@Param("userId") long userId, @Param("cardId") long cardId);

  ProblemReviewCardRow findForUpdate(@Param("userId") long userId, @Param("cardId") long cardId);

  ProblemReviewCardRow findByUserAndSlug(
      @Param("userId") long userId,
      @Param("problemSlug") String problemSlug
  );

  List<ProblemReviewCardRow> findDue(
      @Param("userId") long userId,
      @Param("now") Instant now,
      @Param("limit") int limit
  );

  List<ProblemReviewCardRow> list(
      @Param("userId") long userId,
      @Param("source") String source,
      @Param("mistakeOnly") boolean mistakeOnly,
      @Param("keyword") String keyword,
      @Param("limit") int limit,
      @Param("offset") int offset
  );

  int countDue(@Param("userId") long userId, @Param("now") Instant now);

  int countScheduledBefore(
      @Param("userId") long userId,
      @Param("exclusiveEnd") Instant exclusiveEnd
  );

  Instant findNextDueAt(
      @Param("userId") long userId,
      @Param("after") Instant after,
      @Param("exclusiveEnd") Instant exclusiveEnd
  );

  ProblemReviewCardRow updateArchived(
      @Param("userId") long userId,
      @Param("cardId") long cardId,
      @Param("archived") boolean archived,
      @Param("now") Instant now
  );

  ProblemReviewCardRow updateScheduling(
      @Param("userId") long userId,
      @Param("cardId") long cardId,
      @Param("repetitions") int repetitions,
      @Param("intervalDays") int intervalDays,
      @Param("fsrsState") String fsrsState,
      @Param("fsrsStep") Integer fsrsStep,
      @Param("fsrsStability") BigDecimal fsrsStability,
      @Param("fsrsDifficulty") BigDecimal fsrsDifficulty,
      @Param("lapses") int lapses,
      @Param("dueAt") Instant dueAt,
      @Param("lastRating") String lastRating,
      @Param("reviewedAt") Instant reviewedAt
  );
}
