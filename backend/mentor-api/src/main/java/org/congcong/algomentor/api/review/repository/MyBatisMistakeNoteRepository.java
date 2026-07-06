package org.congcong.algomentor.api.review.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.api.review.mapper.MistakeNoteMapper;
import org.congcong.algomentor.api.review.mapper.model.MistakeNoteRow;
import org.congcong.algomentor.api.review.mapper.model.MistakeNoteUpsertRow;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.review.CardVariant;
import org.congcong.algomentor.mentor.application.review.MistakeNote;
import org.congcong.algomentor.mentor.application.review.MistakeNoteRepository;
import org.congcong.algomentor.mentor.application.review.MistakeReviewException;
import org.congcong.algomentor.mentor.application.review.MistakeSource;
import org.congcong.algomentor.mentor.application.review.ReviewSeed;
import org.congcong.algomentor.mentor.application.review.ReviewCardCache;
import org.congcong.algomentor.mentor.application.review.ReviewRating;
import org.congcong.algomentor.mentor.application.review.SchedulingState;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisMistakeNoteRepository implements MistakeNoteRepository {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };

  private final MistakeNoteMapper mapper;
  private final ObjectMapper objectMapper;

  public MyBatisMistakeNoteRepository(MistakeNoteMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public MistakeNote upsertForReview(PracticeCodeReview review, MistakeSource source, JsonNode sourceDetail, ReviewSeed seed) {
    return toNote(mapper.upsertForReview(new MistakeNoteUpsertRow(
        review.userId(),
        review.problemSlug(),
        source.name(),
        sourceDetail,
        review.planId(),
        review.phaseIndex(),
        review.sessionId(),
        seed.state().repetitions(),
        seed.state().intervalDays(),
        seed.state().fsrsState(),
        seed.state().fsrsStep(),
        seed.state().fsrsStability(),
        seed.state().fsrsDifficulty(),
        seed.state().lapses(),
        seed.dueAt())));
  }

  @Override
  @Transactional
  public MistakeNote mark(long userId, String problemSlug, MistakeSource source, JsonNode sourceDetail, Instant now) {
    return toNote(mapper.mark(new MistakeNoteUpsertRow(
        userId, problemSlug, source.name(), sourceDetail, null, null, null, null, null,
        null, null, null, null, null, null)));
  }

  @Override
  public Optional<MistakeNote> findById(long noteId) {
    return Optional.ofNullable(mapper.findById(noteId)).map(this::toNote);
  }

  @Override
  public Optional<MistakeNote> findForUser(long userId, long noteId) {
    return Optional.ofNullable(mapper.findForUser(userId, noteId)).map(this::toNote);
  }

  @Override
  public Optional<MistakeNote> findByUserAndSlug(long userId, String problemSlug) {
    return Optional.ofNullable(mapper.findByUserAndSlug(userId, problemSlug)).map(this::toNote);
  }

  @Override
  public List<MistakeNote> findDue(long userId, Instant now, int limit) {
    return mapper.findDue(userId, now, limit).stream().map(this::toNote).toList();
  }

  @Override
  public List<MistakeNote> list(
      long userId,
      MistakeSource source,
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
        offset).stream().map(this::toNote).toList();
  }

  @Override
  public int countDue(long userId, Instant now) {
    return mapper.countDue(userId, now);
  }

  @Override
  @Transactional
  public MistakeNote updateArchived(long userId, long noteId, boolean archived, Instant now) {
    return requireRow(mapper.updateArchived(userId, noteId, archived, now));
  }

  @Override
  @Transactional
  public MistakeNote updatePersistentNote(long userId, long noteId, String text, Instant now) {
    return requireRow(mapper.updatePersistentNote(userId, noteId, text, now));
  }

  @Override
  @Transactional
  public MistakeNote updateScheduling(
      long noteId,
      SchedulingState state,
      Instant dueAt,
      ReviewRating lastRating,
      Instant reviewedAt
  ) {
    return requireRow(mapper.updateScheduling(
        noteId,
        state.repetitions(),
        state.intervalDays(),
        state.fsrsState(),
        state.fsrsStep(),
        state.fsrsStability(),
        state.fsrsDifficulty(),
        state.lapses(),
        dueAt,
        lastRating == null ? null : lastRating.name(),
        reviewedAt));
  }

  @Override
  @Transactional
  public void savePendingCard(long noteId, JsonNode cardJson, CardVariant variant, String signature, Instant generatedAt) {
    mapper.savePendingCard(noteId, cardJson, variant.name(), signature, generatedAt);
  }

  private MistakeNote requireRow(MistakeNoteRow row) {
    if (row == null) {
      throw new MistakeReviewException("MISTAKE_NOTE_NOT_FOUND", "错题记录不存在。");
    }
    return toNote(row);
  }

  private MistakeNote toNote(MistakeNoteRow row) {
    return new MistakeNote(
        row.id(),
        row.userId(),
        row.problemSlug(),
        MistakeSource.valueOf(row.source()),
        readMap(row.sourceDetailJson()),
        row.originPlanId(),
        row.originPhaseIndex(),
        row.originPracticeSessionId(),
        new SchedulingState(
            row.repetitions(),
            row.intervalDays(),
            row.lapses(),
            row.fsrsState(),
            row.fsrsStep(),
            row.fsrsStability(),
            row.fsrsDifficulty()),
        row.dueAt(),
        row.lastReviewedAt(),
        row.lastRating() == null ? null : ReviewRating.valueOf(row.lastRating()),
        row.archived(),
        row.userNotePersistent(),
        pendingCard(row),
        row.createdAt(),
        row.updatedAt());
  }

  private ReviewCardCache pendingCard(MistakeNoteRow row) {
    if (row.pendingCardJson() == null || row.pendingCardVariant() == null) {
      return null;
    }
    return new ReviewCardCache(
        row.pendingCardJson(),
        CardVariant.valueOf(row.pendingCardVariant()),
        row.pendingCardSignature(),
        row.pendingCardGeneratedAt());
  }

  private Map<String, Object> readMap(JsonNode node) {
    if (node == null || node.isNull()) {
      return Map.of();
    }
    try {
      return objectMapper.readerFor(MAP_TYPE).readValue(node);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Mistake note JSON parsing failed", exception);
    } catch (IOException exception) {
      throw new IllegalArgumentException("Mistake note JSON reading failed", exception);
    }
  }
}
