package org.congcong.algomentor.mentor.application.review.card;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;
import org.congcong.algomentor.mentor.application.review.ReviewException;
import org.congcong.algomentor.mentor.application.review.attempt.ReviewAttemptRepository;
import org.congcong.algomentor.mentor.application.review.catalog.ReviewProblemCatalog;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNote;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteRepository;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreference;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreferenceService;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsReviewSchedulerService;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsState;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSchedulerProperties;

public class ReviewQueueService {

  private final ReviewCardRepository cardRepository;
  private final ReviewAttemptRepository attemptRepository;
  private final UserProblemNoteRepository noteRepository;
  private final ReviewProblemCatalog problemCatalog;
  private final FsrsReviewSchedulerService schedulerService;
  private final ReviewPreferenceService preferenceService;
  private final ReviewSchedulerProperties schedulerProperties;
  private final Clock clock;

  public ReviewQueueService(
      ReviewCardRepository cardRepository,
      ReviewAttemptRepository attemptRepository,
      UserProblemNoteRepository noteRepository,
      ReviewProblemCatalog problemCatalog,
      FsrsReviewSchedulerService schedulerService,
      ReviewPreferenceService preferenceService,
      ReviewSchedulerProperties schedulerProperties,
      Clock clock
  ) {
    this.cardRepository = Objects.requireNonNull(cardRepository, "cardRepository must not be null");
    this.attemptRepository = Objects.requireNonNull(attemptRepository, "attemptRepository must not be null");
    this.noteRepository = Objects.requireNonNull(noteRepository, "noteRepository must not be null");
    this.problemCatalog = Objects.requireNonNull(problemCatalog, "problemCatalog must not be null");
    this.schedulerService = Objects.requireNonNull(schedulerService, "schedulerService must not be null");
    this.preferenceService = Objects.requireNonNull(preferenceService, "preferenceService must not be null");
    this.schedulerProperties = Objects.requireNonNull(schedulerProperties, "schedulerProperties must not be null");
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public ReviewQueue dueQueue(long userId, int limit) {
    Instant now = Instant.now(clock);
    ReviewPreference preference = preferenceService.get(userId);
    int effectiveLimit = limit <= 0 ? preference.dailyReviewLimit() : Math.min(limit, 100);
    List<ProblemReviewCard> cards = splitQueue(
        cardRepository.findDue(userId, now, 200),
        preference,
        effectiveLimit);
    return new ReviewQueue(cards, Math.min(cardRepository.countDue(userId, now), effectiveLimit));
  }

  public ReviewSummary summary(long userId, String timezone) {
    Instant now = Instant.now(clock);
    ZoneId zoneId = zoneId(timezone);
    Instant tomorrowStart = now.atZone(zoneId)
        .toLocalDate()
        .plusDays(1)
        .atStartOfDay(zoneId)
        .toInstant();
    int dailyCap = schedulerProperties.queueDailyCap();
    int dueCount = Math.min(cardRepository.countDue(userId, now), dailyCap);
    int remainingTodayCount = Math.min(
        cardRepository.countScheduledBefore(userId, tomorrowStart),
        dailyCap);
    Optional<Instant> nextDueAt = dueCount > 0
        ? Optional.empty()
        : cardRepository.findNextDueAt(userId, now, tomorrowStart);
    return new ReviewSummary(dueCount, remainingTodayCount, nextDueAt.orElse(null));
  }

  public ReviewCardContext context(long userId, long cardId, String locale) {
    ProblemReviewCard card = cardRepository.findForUser(userId, cardId)
        .orElseThrow(() -> new ReviewException("REVIEW_CARD_NOT_FOUND", "复习卡不存在。"));
    var problem = problemCatalog.findBySlug(card.problemSlug(), locale)
        .orElseThrow(() -> new ReviewException("REVIEW_PROBLEM_NOT_FOUND", "未找到题目原文。"));
    UserProblemNote note = noteRepository.find(userId, card.problemSlug())
        .orElseGet(() -> UserProblemNote.empty(userId, card.problemSlug()));
    ReviewPreference preference = preferenceService.get(userId);
    Instant now = Instant.now(clock);
    return new ReviewCardContext(
        card,
        problem,
        note,
        attemptRepository.findRecent(userId, cardId, ReviewContractConstants.RECENT_ATTEMPT_LIMIT),
        List.of(
            schedulerService.preview(card, ReviewRating.AGAIN, preference, now),
            schedulerService.preview(card, ReviewRating.HARD, preference, now),
            schedulerService.preview(card, ReviewRating.GOOD, preference, now),
            schedulerService.preview(card, ReviewRating.EASY, preference, now)));
  }

  private List<ProblemReviewCard> splitQueue(
      List<ProblemReviewCard> due,
      ReviewPreference preference,
      int limit
  ) {
    List<ProblemReviewCard> learning = due.stream()
        .filter(card -> isLearning(card) && card.lastReviewedAt() != null)
        .limit(preference.dailyLearningLimit())
        .toList();
    List<ProblemReviewCard> reviews = due.stream()
        .filter(card -> card.scheduling().fsrsState() == FsrsState.REVIEW)
        .limit(preference.dailyReviewLimit())
        .toList();
    List<ProblemReviewCard> newLike = due.stream()
        .filter(card -> isLearning(card) && card.lastReviewedAt() == null)
        .limit(preference.dailyNewLimit())
        .toList();
    return Stream.of(learning, reviews, newLike)
        .flatMap(List::stream)
        .limit(limit)
        .toList();
  }

  private boolean isLearning(ProblemReviewCard card) {
    return card.scheduling().fsrsState() == FsrsState.LEARNING
        || card.scheduling().fsrsState() == FsrsState.RELEARNING;
  }

  private ZoneId zoneId(String timezone) {
    String normalized = timezone == null || timezone.isBlank() ? "UTC" : timezone.trim();
    try {
      return ZoneId.of(normalized);
    } catch (DateTimeException exception) {
      throw new ReviewException("REVIEW_TIMEZONE_INVALID", "时区参数无效。");
    }
  }
}
