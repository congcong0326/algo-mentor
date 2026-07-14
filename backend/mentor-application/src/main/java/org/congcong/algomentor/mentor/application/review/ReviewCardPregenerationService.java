package org.congcong.algomentor.mentor.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReviewCardPregenerationService {

  private static final Logger log = LoggerFactory.getLogger(ReviewCardPregenerationService.class);

  private final Executor executor;
  private final AiDailyUsageStore usageStore;
  private final MistakeNoteRepository repository;
  private final ReviewCardService cardService;
  private final ReviewCardProperties properties;
  private final MistakeReviewMetrics metrics;
  private final Clock clock;

  public ReviewCardPregenerationService(
      Executor executor,
      AiDailyUsageStore usageStore,
      MistakeNoteRepository repository,
      ReviewCardService cardService,
      ReviewCardProperties properties,
      MistakeReviewMetrics metrics,
      Clock clock
  ) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    this.usageStore = Objects.requireNonNull(usageStore, "usageStore must not be null");
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.cardService = Objects.requireNonNull(cardService, "cardService must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    this.cardService.setPregenerationService(this);
  }

  public void enqueue(long noteId) {
    executor.execute(() -> safelyGenerate(noteId));
  }

  void safelyGenerate(long noteId) {
    try {
      MistakeNote note = repository.findById(noteId).orElse(null);
      if (note == null || note.archived()) {
        return;
      }
      String signature = cardService.signature(note);
      if (cardService.cacheHit(note, signature)) {
        metrics.recordCardGeneration(CardGenerationOutcome.CACHE_HIT);
        return;
      }
      if (!cardService.canGenerateAi(note)) {
        log.info("Review card generation skipped by AI runtime policy. noteId={} userId={}",
            note.id(), note.userId());
        metrics.recordCardGeneration(CardGenerationOutcome.FALLBACK_RULE);
        return;
      }
      LocalDate today = LocalDate.now(clock);
      if (!usageStore.tryConsumeRequest(
          note.userId(),
          today,
          MistakeReviewConstants.QUOTA_SCOPE,
          properties.dailyLimit())) {
        log.info("Review card generation quota exceeded. noteId={} userId={} quotaDate={}",
            note.id(), note.userId(), today);
        metrics.recordCardGeneration(CardGenerationOutcome.QUOTA_EXCEEDED);
        return;
      }
      ReviewCard card = cardService.generate(note);
      if (card == null) {
        metrics.recordCardGeneration(CardGenerationOutcome.FAILED);
        return;
      }
      JsonNode cardJson = cardService.toJson(card);
      repository.savePendingCard(noteId, cardJson, CardVariant.AI_GENERATED, signature, Instant.now(clock));
      metrics.recordCardGeneration(CardGenerationOutcome.GENERATED);
    } catch (RuntimeException exception) {
      log.warn("Review card pregeneration failed. noteId={} exceptionType={}",
          noteId, exception.getClass().getSimpleName(), exception);
      metrics.recordCardGeneration(CardGenerationOutcome.FAILED);
    }
  }
}
