package org.congcong.algomentor.mentor.application.practice;

import java.util.List;
import java.util.Optional;

public interface PracticeCodeReviewRepository {

  PracticeCodeReview save(PracticeCodeReviewDraft draft);

  Optional<PracticeCodeReviewSummary> findLatestSummary(long userId, long sessionId);

  Optional<PracticeCodeReview> findLatest(long userId, long sessionId);

  List<PracticeCodeReviewSummary> findSummaries(long userId, long sessionId);

  Optional<PracticeCodeReview> findById(long userId, long sessionId, long reviewId);

  Optional<PracticeCodeReview> findByUserMessage(long userId, long sessionId, long userMessageId);

  static PracticeCodeReviewRepository empty() {
    return EmptyPracticeCodeReviewRepository.INSTANCE;
  }

  final class EmptyPracticeCodeReviewRepository implements PracticeCodeReviewRepository {

    private static final EmptyPracticeCodeReviewRepository INSTANCE = new EmptyPracticeCodeReviewRepository();

    private EmptyPracticeCodeReviewRepository() {
    }

    @Override
    public PracticeCodeReview save(PracticeCodeReviewDraft draft) {
      throw new UnsupportedOperationException("Practice code review repository is not configured");
    }

    @Override
    public Optional<PracticeCodeReviewSummary> findLatestSummary(long userId, long sessionId) {
      return Optional.empty();
    }

    @Override
    public Optional<PracticeCodeReview> findLatest(long userId, long sessionId) {
      return Optional.empty();
    }

    @Override
    public List<PracticeCodeReviewSummary> findSummaries(long userId, long sessionId) {
      return List.of();
    }

    @Override
    public Optional<PracticeCodeReview> findById(long userId, long sessionId, long reviewId) {
      return Optional.empty();
    }

    @Override
    public Optional<PracticeCodeReview> findByUserMessage(long userId, long sessionId, long userMessageId) {
      return Optional.empty();
    }
  }
}
