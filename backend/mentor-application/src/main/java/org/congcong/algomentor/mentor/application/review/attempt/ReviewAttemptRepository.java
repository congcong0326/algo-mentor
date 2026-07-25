package org.congcong.algomentor.mentor.application.review.attempt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewAttemptRepository {

  Optional<ProblemReviewAttempt> findByUserAndClientAttemptId(long userId, UUID clientAttemptId);

  Optional<ProblemReviewAttempt> insertIfAbsent(ProblemReviewAttempt attempt);

  List<ProblemReviewAttempt> findRecent(long userId, long reviewCardId, int limit);
}
