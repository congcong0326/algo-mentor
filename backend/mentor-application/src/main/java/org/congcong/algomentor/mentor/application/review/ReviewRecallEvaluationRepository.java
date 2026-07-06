package org.congcong.algomentor.mentor.application.review;

import java.util.Optional;

public interface ReviewRecallEvaluationRepository {

  ReviewRecallEvaluation save(ReviewRecallEvaluation evaluation);

  Optional<ReviewRecallEvaluation> findForUser(long userId, long evaluationId);

  static ReviewRecallEvaluationRepository memory() {
    return new ReviewRecallEvaluationRepository() {
      private final java.util.concurrent.atomic.AtomicLong ids = new java.util.concurrent.atomic.AtomicLong();
      private final java.util.Map<Long, ReviewRecallEvaluation> evaluations = new java.util.concurrent.ConcurrentHashMap<>();

      @Override
      public ReviewRecallEvaluation save(ReviewRecallEvaluation evaluation) {
        long id = ids.incrementAndGet();
        ReviewRecallEvaluation saved = new ReviewRecallEvaluation(
            id,
            evaluation.noteId(),
            evaluation.userId(),
            evaluation.recallText(),
            evaluation.transientNote(),
            evaluation.suggestedRating(),
            evaluation.hitPoints(),
            evaluation.missedPoints(),
            evaluation.gapSummary(),
            evaluation.aiSuggested(),
            evaluation.createdAt());
        evaluations.put(id, saved);
        return saved;
      }

      @Override
      public Optional<ReviewRecallEvaluation> findForUser(long userId, long evaluationId) {
        ReviewRecallEvaluation evaluation = evaluations.get(evaluationId);
        return evaluation == null || evaluation.userId() != userId ? Optional.empty() : Optional.of(evaluation);
      }
    };
  }
}
