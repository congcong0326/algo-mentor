package org.congcong.algomentor.api.review.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.review.mapper.ReviewRecallEvaluationMapper;
import org.congcong.algomentor.api.review.mapper.model.ReviewRecallEvaluationRow;
import org.congcong.algomentor.mentor.application.review.ReviewRating;
import org.congcong.algomentor.mentor.application.review.ReviewRecallEvaluation;
import org.congcong.algomentor.mentor.application.review.ReviewRecallEvaluationRepository;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisReviewRecallEvaluationRepository implements ReviewRecallEvaluationRepository {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
  };

  private final ReviewRecallEvaluationMapper mapper;
  private final ObjectMapper objectMapper;

  public MyBatisReviewRecallEvaluationRepository(ReviewRecallEvaluationMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public ReviewRecallEvaluation save(ReviewRecallEvaluation evaluation) {
    return toEvaluation(mapper.insert(
        evaluation.noteId(),
        evaluation.userId(),
        evaluation.recallText(),
        evaluation.transientNote(),
        evaluation.suggestedRating() == null ? null : evaluation.suggestedRating().name(),
        objectMapper.valueToTree(evaluation.hitPoints()),
        objectMapper.valueToTree(evaluation.missedPoints()),
        evaluation.gapSummary(),
        evaluation.aiSuggested(),
        evaluation.createdAt()));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ReviewRecallEvaluation> findForUser(long userId, long evaluationId) {
    return Optional.ofNullable(mapper.findForUser(userId, evaluationId)).map(this::toEvaluation);
  }

  private ReviewRecallEvaluation toEvaluation(ReviewRecallEvaluationRow row) {
    return new ReviewRecallEvaluation(
        row.id(),
        row.mistakeNoteId(),
        row.userId(),
        row.recallText(),
        row.transientNote(),
        row.suggestedRating() == null ? null : ReviewRating.valueOf(row.suggestedRating()),
        readList(row.hitPointsJson()),
        readList(row.missedPointsJson()),
        row.gapSummary(),
        row.aiSuggested(),
        row.createdAt());
  }

  private List<String> readList(com.fasterxml.jackson.databind.JsonNode node) {
    if (node == null || node.isNull()) {
      return List.of();
    }
    return objectMapper.convertValue(node, STRING_LIST);
  }
}
