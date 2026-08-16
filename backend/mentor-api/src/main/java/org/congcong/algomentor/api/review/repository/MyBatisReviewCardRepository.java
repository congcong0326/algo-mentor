package org.congcong.algomentor.api.review.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.api.review.mapper.ProblemReviewCardMapper;
import org.congcong.algomentor.api.review.mapper.model.ProblemReviewCardRow;
import org.congcong.algomentor.api.review.mapper.model.ProblemReviewCardUpsertRow;
import org.congcong.algomentor.mentor.application.review.ReviewException;
import org.congcong.algomentor.mentor.application.review.card.ProblemReviewCard;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardRepository;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardSource;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsState;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeed;
import org.congcong.algomentor.mentor.application.review.schedule.SchedulingState;

public class MyBatisReviewCardRepository implements ReviewCardRepository {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };

  private final ProblemReviewCardMapper mapper;
  private final ObjectMapper objectMapper;

  public MyBatisReviewCardRepository(ProblemReviewCardMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public ProblemReviewCard upsertForReview(
      long userId,
      String problemSlug,
      ReviewCardSource source,
      JsonNode sourceDetail,
      ReviewSeed seed
  ) {
    SchedulingState state = seed.state();
    return toCard(mapper.upsertForReview(new ProblemReviewCardUpsertRow(
        userId,
        problemSlug,
        source.name(),
        sourceDetail,
        state.repetitions(),
        state.intervalDays(),
        state.fsrsState().name(),
        state.fsrsStep(),
        state.fsrsStability(),
        state.fsrsDifficulty(),
        state.lapses(),
        seed.dueAt(),
        seed.reviewedAt())));
  }

  @Override
  public ProblemReviewCard mark(
      long userId,
      String problemSlug,
      ReviewCardSource source,
      JsonNode sourceDetail,
      Instant now
  ) {
    return toCard(mapper.mark(userId, problemSlug, source.name(), sourceDetail, now));
  }

  @Override
  public Optional<ProblemReviewCard> findByUserAndSlug(long userId, String problemSlug) {
    return Optional.ofNullable(mapper.findByUserAndSlug(userId, problemSlug)).map(this::toCard);
  }

  @Override
  public Optional<ProblemReviewCard> findForUser(long userId, long cardId) {
    return Optional.ofNullable(mapper.findForUser(userId, cardId)).map(this::toCard);
  }

  @Override
  public Optional<ProblemReviewCard> findForUpdate(long userId, long cardId) {
    return Optional.ofNullable(mapper.findForUpdate(userId, cardId)).map(this::toCard);
  }

  @Override
  public List<ProblemReviewCard> findDue(long userId, Instant now, int limit) {
    return mapper.findDue(userId, now, limit).stream().map(this::toCard).toList();
  }

  @Override
  public List<ProblemReviewCard> list(
      long userId,
      ReviewCardSource source,
      boolean mistakeOnly,
      String keyword,
      int limit,
      int offset
  ) {
    return mapper.list(
        userId,
        source == null ? null : source.name(),
        mistakeOnly,
        keyword,
        limit,
        offset).stream().map(this::toCard).toList();
  }

  @Override
  public int countDue(long userId, Instant now) {
    return mapper.countDue(userId, now);
  }

  @Override
  public int countScheduledBefore(long userId, Instant exclusiveEnd) {
    return mapper.countScheduledBefore(userId, exclusiveEnd);
  }

  @Override
  public Optional<Instant> findNextDueAt(long userId, Instant after, Instant exclusiveEnd) {
    return Optional.ofNullable(mapper.findNextDueAt(userId, after, exclusiveEnd));
  }

  @Override
  public ProblemReviewCard updateArchived(long userId, long cardId, boolean archived, Instant now) {
    return requireRow(mapper.updateArchived(userId, cardId, archived, now));
  }

  @Override
  public ProblemReviewCard updateScheduling(
      long userId,
      long cardId,
      SchedulingState state,
      Instant dueAt,
      ReviewRating lastRating,
      Instant reviewedAt
  ) {
    return requireRow(mapper.updateScheduling(
        userId,
        cardId,
        state.repetitions(),
        state.intervalDays(),
        state.fsrsState().name(),
        state.fsrsStep(),
        state.fsrsStability(),
        state.fsrsDifficulty(),
        state.lapses(),
        dueAt,
        lastRating == null ? null : lastRating.name(),
        reviewedAt));
  }

  private ProblemReviewCard requireRow(ProblemReviewCardRow row) {
    if (row == null) {
      throw new ReviewException("REVIEW_CARD_NOT_FOUND", "复习卡不存在。");
    }
    return toCard(row);
  }

  private ProblemReviewCard toCard(ProblemReviewCardRow row) {
    return new ProblemReviewCard(
        row.id(),
        row.userId(),
        row.problemSlug(),
        ReviewCardSource.valueOf(row.source()),
        readMap(row.sourceDetailJson()),
        new SchedulingState(
            row.repetitions(),
            row.intervalDays(),
            row.lapses(),
            FsrsState.valueOf(row.fsrsState()),
            row.fsrsStep(),
            row.fsrsStability(),
            row.fsrsDifficulty()),
        row.dueAt(),
        row.lastReviewedAt(),
        row.lastRating() == null ? null : ReviewRating.valueOf(row.lastRating()),
        row.archived(),
        row.createdAt(),
        row.updatedAt());
  }

  private Map<String, Object> readMap(JsonNode node) {
    if (node == null || node.isNull()) {
      return Map.of();
    }
    return objectMapper.convertValue(node, MAP_TYPE);
  }
}
