package org.congcong.algomentor.api.practice.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewInsertRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewSessionLockRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewSummaryRow;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewDraft;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewEvidence;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewSummary;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisPracticeCodeReviewRepository implements PracticeCodeReviewRepository {

  private static final TypeReference<List<PracticeCodeReviewEvidence>> EVIDENCE_LIST = new TypeReference<>() {
  };
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
  };

  private final PracticeCodeReviewMapper mapper;
  private final ObjectMapper objectMapper;

  public MyBatisPracticeCodeReviewRepository(PracticeCodeReviewMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public PracticeCodeReview save(PracticeCodeReviewDraft draft) {
    PracticeCodeReviewSessionLockRow session = mapper.lockSessionForReviewInsert(draft.userId(), draft.sessionId());
    if (session == null) {
      throw sessionNotFound();
    }
    PracticeCodeReviewRow row = mapper.insert(toInsertRow(draft, session));
    if (row == null) {
      throw sessionNotFound();
    }
    return toReview(row);
  }

  @Override
  public Optional<PracticeCodeReviewSummary> findLatestSummary(long userId, long sessionId) {
    return Optional.ofNullable(mapper.findLatestSummary(userId, sessionId)).map(this::toSummary);
  }

  @Override
  public Optional<PracticeCodeReview> findLatest(long userId, long sessionId) {
    return Optional.ofNullable(mapper.findLatest(userId, sessionId)).map(this::toReview);
  }

  @Override
  public List<PracticeCodeReviewSummary> findSummaries(long userId, long sessionId) {
    return mapper.findSummaries(userId, sessionId).stream().map(this::toSummary).toList();
  }

  @Override
  public Optional<PracticeCodeReview> findById(long userId, long sessionId, long reviewId) {
    return Optional.ofNullable(mapper.findById(userId, sessionId, reviewId)).map(this::toReview);
  }

  @Override
  public Optional<PracticeCodeReview> findByUserMessage(long userId, long sessionId, long userMessageId) {
    return Optional.ofNullable(mapper.findByUserMessage(userId, sessionId, userMessageId)).map(this::toReview);
  }

  private PracticeCodeReviewInsertRow toInsertRow(PracticeCodeReviewDraft draft, PracticeCodeReviewSessionLockRow session) {
    return new PracticeCodeReviewInsertRow(
        draft.userId(),
        session.planId(),
        session.phaseIndex(),
        session.problemSlug(),
        session.id(),
        draft.userMessageId(),
        draft.assistantMessageId(),
        draft.agentRunDbId(),
        draft.rawCode(),
        draft.normalizedCode(),
        draft.language(),
        json(draft.evidence()),
        defaultText(draft.contextSummary()),
        draft.score().total(),
        draft.score().correctness(),
        draft.score().complexity(),
        draft.score().edgeCases(),
        draft.score().codeQuality(),
        draft.score().problemFit(),
        draft.passed(),
        json(draft.deductionReasons()),
        json(draft.improvementSuggestions()),
        defaultText(draft.reviewMarkdown()));
  }

  private IllegalStateException sessionNotFound() {
    return new IllegalStateException("Practice code review session was not found or is not writable");
  }

  private PracticeCodeReview toReview(PracticeCodeReviewRow row) {
    return new PracticeCodeReview(
        row.id(),
        row.userId(),
        row.planId(),
        row.phaseIndex(),
        row.problemSlug(),
        row.sessionId(),
        row.versionNo(),
        row.userMessageId(),
        row.assistantMessageId(),
        row.agentRunDbId(),
        row.rawCode(),
        row.normalizedCode(),
        row.language(),
        read(row.detectionEvidenceJson(), EVIDENCE_LIST),
        row.contextSummary(),
        new PracticeCodeReviewScore(
            row.correctnessScore(),
            row.complexityScore(),
            row.edgeCaseScore(),
            row.codeQualityScore(),
            row.problemFitScore(),
            row.totalScore()),
        row.passed(),
        read(row.deductionReasonsJson(), STRING_LIST),
        read(row.improvementSuggestionsJson(), STRING_LIST),
        row.reviewMarkdown(),
        row.createdAt());
  }

  private PracticeCodeReviewSummary toSummary(PracticeCodeReviewSummaryRow row) {
    return new PracticeCodeReviewSummary(
        row.id(),
        row.versionNo(),
        row.language(),
        row.totalScore(),
        row.passed(),
        row.createdAt());
  }

  private JsonNode json(Object value) {
    return value == null ? null : objectMapper.valueToTree(value);
  }

  private String defaultText(String value) {
    return value == null ? "" : value;
  }

  private <T> T read(JsonNode node, TypeReference<T> type) {
    try {
      return objectMapper.readerFor(type).readValue(node);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Practice code review JSON parsing failed", exception);
    } catch (IOException exception) {
      throw new IllegalArgumentException("Practice code review JSON reading failed", exception);
    }
  }
}
