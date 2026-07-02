package org.congcong.algomentor.mentor.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MistakeNoteService {

  private static final Logger log = LoggerFactory.getLogger(MistakeNoteService.class);

  private final MistakeNoteRepository repository;
  private final ReviewCardPregenerationService pregenerationService;
  private final ReviewSeedPolicy seedPolicy;
  private final ObjectMapper objectMapper;
  private final MistakeReviewMetrics metrics;
  private final Clock clock;

  public MistakeNoteService(
      MistakeNoteRepository repository,
      ReviewCardPregenerationService pregenerationService,
      ReviewSeedPolicy seedPolicy,
      ObjectMapper objectMapper,
      MistakeReviewMetrics metrics,
      Clock clock
  ) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.pregenerationService = Objects.requireNonNull(pregenerationService, "pregenerationService must not be null");
    this.seedPolicy = Objects.requireNonNull(seedPolicy, "seedPolicy must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public void ingestFromReview(PracticeCodeReview review) {
    Objects.requireNonNull(review, "review must not be null");
    boolean passed = review.passed()
        && review.score().total().compareTo(PracticeCodeReviewConstants.PASS_SCORE) >= 0;
    MistakeSource source = passed ? MistakeSource.REVIEW_PASSED : MistakeSource.REVIEW_FAILED;
    ReviewSeed seed = seedPolicy.forReview(review, passed, Instant.now(clock));
    MistakeNote note = repository.upsertForReview(review, source, sourceDetail(review, seed), seed);
    metrics.recordNoteIngest(source);
    metrics.recordSeed(seed.bucket());
    pregenerationService.enqueue(note.id());
    log.info("Review note ingested from code review. noteId={} reviewId={} userId={} problemSlug={} source={} seed={}",
        note.id(), review.id(), review.userId(), review.problemSlug(), source, seed.bucket());
  }

  public MistakeNote mark(long userId, String problemSlug) {
    MistakeNote note = repository.mark(
        userId,
        requireText(problemSlug, "problemSlug"),
        MistakeSource.USER_MARKED,
        objectMapper.valueToTree(Map.of("source", MistakeSource.USER_MARKED.name())),
        Instant.now(clock));
    metrics.recordNoteIngest(MistakeSource.USER_MARKED);
    pregenerationService.enqueue(note.id());
    return note;
  }

  public List<MistakeNote> list(
      long userId,
      MasteryState state,
      MistakeSource source,
      boolean mistakeOnly,
      String keyword,
      int limit,
      int offset
  ) {
    return repository.list(userId, state, source, mistakeOnly, keyword, clampLimit(limit), Math.max(0, offset));
  }

  public MistakeNote archive(long userId, long noteId, boolean archived) {
    return repository.updateArchived(userId, noteId, archived, Instant.now(clock));
  }

  public MistakeNote updatePersistentNote(long userId, long noteId, String text) {
    return repository.updatePersistentNote(
        userId,
        noteId,
        truncate(text, MistakeReviewConstants.PERSISTENT_NOTE_MAX_CHARS),
        Instant.now(clock));
  }

  public MistakeNote get(long userId, long noteId) {
    return repository.findForUser(userId, noteId)
        .orElseThrow(() -> new MistakeReviewException("MISTAKE_NOTE_NOT_FOUND", "错题记录不存在。"));
  }

  private JsonNode sourceDetail(PracticeCodeReview review, ReviewSeed seed) {
    return objectMapper.valueToTree(Map.of(
        "latestReviewId", review.id(),
        "latestReviewScore", review.score().total(),
        "latestReviewPassed", review.passed(),
        "deductionReasons", review.deductionReasons(),
        "improvementSuggestions", review.improvementSuggestions(),
        "language", review.language(),
        "lowConfidence", seed.lowConfidence(),
        "seedBucket", seed.bucket().name()));
  }

  private String requireText(String text, String field) {
    if (text == null || text.isBlank()) {
      throw new MistakeReviewException("MISTAKE_NOTE_INVALID_REQUEST", field + " 不能为空。");
    }
    return text.trim();
  }

  private int clampLimit(int limit) {
    if (limit <= 0) {
      return 20;
    }
    return Math.min(limit, 100);
  }

  private String truncate(String text, int maxChars) {
    if (text == null) {
      return "";
    }
    return text.length() <= maxChars ? text : text.substring(0, maxChars);
  }
}
