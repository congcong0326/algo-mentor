package org.congcong.algomentor.mentor.application.review.card;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewConstants;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;
import org.congcong.algomentor.mentor.application.review.ReviewException;
import org.congcong.algomentor.mentor.application.review.catalog.ReviewProblemCatalog;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeed;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeedPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReviewCardService {

  private static final Logger log = LoggerFactory.getLogger(ReviewCardService.class);

  private final ReviewCardRepository repository;
  private final ReviewSeedPolicy seedPolicy;
  private final ObjectMapper objectMapper;
  private final ReviewMetrics metrics;
  private final ReviewProblemCatalog problemCatalog;
  private final Clock clock;

  public ReviewCardService(
      ReviewCardRepository repository,
      ReviewSeedPolicy seedPolicy,
      ObjectMapper objectMapper,
      ReviewMetrics metrics,
      ReviewProblemCatalog problemCatalog,
      Clock clock
  ) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.seedPolicy = Objects.requireNonNull(seedPolicy, "seedPolicy must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.metrics = metrics == null ? ReviewMetrics.NOOP : metrics;
    this.problemCatalog = Objects.requireNonNull(problemCatalog, "problemCatalog must not be null");
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public void ingestFromReview(PracticeCodeReview review) {
    Objects.requireNonNull(review, "review must not be null");
    boolean passed = review.passed()
        && review.score().total().compareTo(PracticeCodeReviewConstants.PASS_SCORE) >= 0;
    ReviewCardSource source = passed ? ReviewCardSource.REVIEW_PASSED : ReviewCardSource.REVIEW_FAILED;
    Instant now = Instant.now(clock);
    String difficulty = problemCatalog.findBySlug(review.problemSlug()).map(item -> item.difficulty()).orElse(null);
    ReviewSeed seed = seedPolicy.forReview(review, passed, now, difficulty);
    Optional<ProblemReviewCard> previous = repository.findByUserAndSlug(review.userId(), review.problemSlug());
    ProblemReviewCard card = repository.upsertForReview(
        review.userId(),
        review.problemSlug(),
        source,
        sourceDetail(review, seed),
        seed);
    metrics.recordCardIngest(source, outcome(source, previous, card));
    metrics.recordSeed(seed.bucket());
    log.info("Review card ingested from code review. cardId={} reviewId={} userId={} problemSlug={} source={} seed={}",
        card.id(), review.id(), review.userId(), review.problemSlug(), source, seed.bucket());
  }

  public ProblemReviewCard mark(long userId, String problemSlug) {
    String normalizedSlug = requireProblem(problemSlug);
    Optional<ProblemReviewCard> previous = repository.findByUserAndSlug(userId, normalizedSlug);
    ProblemReviewCard card = repository.mark(
        userId,
        normalizedSlug,
        ReviewCardSource.USER_MARKED,
        markSourceDetail(normalizedSlug),
        Instant.now(clock));
    metrics.recordCardIngest(
        ReviewCardSource.USER_MARKED,
        previous.isEmpty() ? ReviewCardIngestOutcome.INSERTED : ReviewCardIngestOutcome.UPDATED);
    return card;
  }

  public List<ProblemReviewCard> list(
      long userId,
      ReviewCardSource source,
      boolean mistakeOnly,
      String keyword,
      int limit,
      int offset
  ) {
    return repository.list(userId, source, mistakeOnly, keyword, clampLimit(limit), Math.max(0, offset));
  }

  public ProblemReviewCard archive(long userId, long cardId, boolean archived) {
    return repository.updateArchived(userId, cardId, archived, Instant.now(clock));
  }

  public ProblemReviewCard get(long userId, long cardId) {
    return repository.findForUser(userId, cardId)
        .orElseThrow(() -> new ReviewException("REVIEW_CARD_NOT_FOUND", "复习卡不存在。"));
  }

  private String requireProblem(String problemSlug) {
    if (problemSlug == null || problemSlug.isBlank()) {
      throw new ReviewException("PROBLEM_SLUG_REQUIRED", "题目 slug 不能为空。");
    }
    String normalized = problemSlug.strip();
    if (problemCatalog.findBySlug(normalized).isEmpty()) {
      throw new ReviewException("REVIEW_PROBLEM_NOT_FOUND", "未找到题目。");
    }
    return normalized;
  }

  private JsonNode sourceDetail(PracticeCodeReview review, ReviewSeed seed) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put(ReviewContractConstants.METADATA_LATEST_REVIEW_ID, review.id());
    detail.put(ReviewContractConstants.METADATA_LATEST_REVIEW_SCORE, review.score().total());
    detail.put(ReviewContractConstants.METADATA_LATEST_REVIEW_PASSED, review.passed());
    detail.put(ReviewContractConstants.METADATA_DEDUCTION_REASONS, review.deductionReasons());
    detail.put(ReviewContractConstants.METADATA_IMPROVEMENT_SUGGESTIONS, review.improvementSuggestions());
    detail.put(ReviewContractConstants.METADATA_LANGUAGE, review.language());
    detail.put(ReviewContractConstants.METADATA_LOW_CONFIDENCE, seed.lowConfidence());
    detail.put(ReviewContractConstants.METADATA_SEED_BUCKET, seed.bucket().name());
    detail.put(ReviewContractConstants.METADATA_INITIAL_RATING, seed.initialRating().name());
    enrichProblemDetail(review.problemSlug(), detail);
    return objectMapper.valueToTree(detail);
  }

  private JsonNode markSourceDetail(String problemSlug) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put(ReviewContractConstants.METADATA_SOURCE, ReviewCardSource.USER_MARKED.name());
    enrichProblemDetail(problemSlug, detail);
    return objectMapper.valueToTree(detail);
  }

  private void enrichProblemDetail(String problemSlug, Map<String, Object> detail) {
    try {
      problemCatalog.findBySlug(problemSlug).ifPresent(snapshot -> {
        putIfNotBlank(detail, ReviewContractConstants.METADATA_TITLE_CN, snapshot.title());
        putIfNotBlank(detail, ReviewContractConstants.METADATA_DIFFICULTY, snapshot.difficulty());
        putIfNotBlank(detail, ReviewContractConstants.METADATA_STATEMENT_SUMMARY, snapshot.statementSummary());
      });
    } catch (RuntimeException exception) {
      log.warn("Review problem catalog lookup failed. problemSlug={} exceptionType={}",
          problemSlug,
          exception.getClass().getSimpleName());
    }
  }

  private void putIfNotBlank(Map<String, Object> detail, String key, String value) {
    if (value != null && !value.isBlank()) {
      detail.put(key, value.strip());
    }
  }

  private ReviewCardIngestOutcome outcome(
      ReviewCardSource source,
      Optional<ProblemReviewCard> previous,
      ProblemReviewCard current
  ) {
    if (previous.isEmpty()) {
      return ReviewCardIngestOutcome.INSERTED;
    }
    if (source == ReviewCardSource.REVIEW_FAILED
        && current.scheduling().lapses() > previous.get().scheduling().lapses()) {
      return ReviewCardIngestOutcome.LAPSED;
    }
    return ReviewCardIngestOutcome.UPDATED;
  }

  private int clampLimit(int limit) {
    return limit <= 0 ? 20 : Math.min(limit, 100);
  }
}
