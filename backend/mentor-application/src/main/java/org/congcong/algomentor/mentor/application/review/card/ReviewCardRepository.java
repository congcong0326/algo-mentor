package org.congcong.algomentor.mentor.application.review.card;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeed;
import org.congcong.algomentor.mentor.application.review.schedule.SchedulingState;

public interface ReviewCardRepository {

  ProblemReviewCard upsertForReview(
      long userId,
      String problemSlug,
      ReviewCardSource source,
      JsonNode sourceDetail,
      ReviewSeed seed
  );

  ProblemReviewCard mark(
      long userId,
      String problemSlug,
      ReviewCardSource source,
      JsonNode sourceDetail,
      Instant now
  );

  Optional<ProblemReviewCard> findByUserAndSlug(long userId, String problemSlug);

  Optional<ProblemReviewCard> findForUser(long userId, long cardId);

  Optional<ProblemReviewCard> findForUpdate(long userId, long cardId);

  List<ProblemReviewCard> findDue(long userId, Instant now, int limit);

  List<ProblemReviewCard> list(
      long userId,
      ReviewCardSource source,
      boolean mistakeOnly,
      String keyword,
      int limit,
      int offset
  );

  int countDue(long userId, Instant now);

  int countScheduledBefore(long userId, Instant exclusiveEnd);

  Optional<Instant> findNextDueAt(long userId, Instant after, Instant exclusiveEnd);

  ProblemReviewCard updateArchived(long userId, long cardId, boolean archived, Instant now);

  ProblemReviewCard updateScheduling(
      long userId,
      long cardId,
      SchedulingState state,
      Instant dueAt,
      ReviewRating lastRating,
      Instant reviewedAt
  );
}
