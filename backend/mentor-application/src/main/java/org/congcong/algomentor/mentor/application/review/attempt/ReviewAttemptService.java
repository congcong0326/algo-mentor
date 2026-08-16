package org.congcong.algomentor.mentor.application.review.attempt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.review.ReviewException;
import org.congcong.algomentor.mentor.application.review.card.ProblemReviewCard;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardRepository;
import org.congcong.algomentor.mentor.application.review.card.ReviewMetrics;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreference;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreferenceService;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsReviewSchedulerService;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.springframework.transaction.annotation.Transactional;

public class ReviewAttemptService {

  private final ReviewCardRepository cardRepository;
  private final ReviewAttemptRepository attemptRepository;
  private final FsrsReviewSchedulerService schedulerService;
  private final ReviewPreferenceService preferenceService;
  private final ReviewMetrics metrics;
  private final Clock clock;

  public ReviewAttemptService(
      ReviewCardRepository cardRepository,
      ReviewAttemptRepository attemptRepository,
      FsrsReviewSchedulerService schedulerService,
      ReviewPreferenceService preferenceService,
      ReviewMetrics metrics,
      Clock clock
  ) {
    this.cardRepository = Objects.requireNonNull(cardRepository, "cardRepository must not be null");
    this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository must not be null");
    this.schedulerService = Objects.requireNonNull(schedulerService, "schedulerService must not be null");
    this.preferenceService = Objects.requireNonNull(preferenceService, "preferenceService must not be null");
    this.metrics = metrics == null ? ReviewMetrics.NOOP : metrics;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  @Transactional
  public ReviewAttemptResult submit(
      long userId,
      long reviewCardId,
      UUID clientAttemptId,
      ReviewRating rating
  ) {
    return submit(userId, reviewCardId, clientAttemptId, rating, ZoneOffset.UTC);
  }

  @Transactional
  public ReviewAttemptResult submit(
      long userId,
      long reviewCardId,
      UUID clientAttemptId,
      ReviewRating rating,
      ZoneId userZone
  ) {
    if (clientAttemptId == null) {
      throw new ReviewException("CLIENT_ATTEMPT_ID_REQUIRED", "clientAttemptId 不能为空。");
    }
    Objects.requireNonNull(rating, "rating must not be null");
    Objects.requireNonNull(userZone, "userZone must not be null");
    ProblemReviewCard card = cardRepository.findForUpdate(userId, reviewCardId)
        .orElseThrow(() -> new ReviewException("REVIEW_CARD_NOT_FOUND", "复习卡不存在。"));
    var existing = attemptRepository.findByUserAndClientAttemptId(userId, clientAttemptId);
    if (existing.isPresent()) {
      return new ReviewAttemptResult(existing.get(), true);
    }

    Instant reviewedAt = Instant.now(clock);
    ReviewPreference preference = preferenceService.get(userId);
    FsrsReviewSchedulerService.Scheduled scheduled = schedulerService.apply(
        card,
        rating,
        preference,
        reviewedAt,
        userZone);
    ProblemReviewAttempt pending = new ProblemReviewAttempt(
        0,
        card.id(),
        userId,
        clientAttemptId,
        rating,
        ReviewSchedulingSnapshot.before(card),
        ReviewSchedulingSnapshot.after(scheduled, reviewedAt),
        reviewedAt);
    var inserted = attemptRepository.insertIfAbsent(pending);
    if (inserted.isEmpty()) {
      ProblemReviewAttempt duplicate = attemptRepository.findByUserAndClientAttemptId(userId, clientAttemptId)
          .orElseThrow(() -> new ReviewException("REVIEW_ATTEMPT_CONFLICT", "复习提交发生冲突，请重试。"));
      return new ReviewAttemptResult(duplicate, true);
    }
    cardRepository.updateScheduling(
        userId,
        card.id(),
        scheduled.state(),
        scheduled.dueAt(),
        rating,
        reviewedAt);
    metrics.recordAttemptSubmit();
    return new ReviewAttemptResult(inserted.get(), false);
  }

  public java.util.List<ProblemReviewAttempt> history(long userId, long reviewCardId, int limit) {
    cardRepository.findForUser(userId, reviewCardId)
        .orElseThrow(() -> new ReviewException("REVIEW_CARD_NOT_FOUND", "复习卡不存在。"));
    return attemptRepository.findRecent(userId, reviewCardId, Math.min(Math.max(limit, 1), 100));
  }
}
