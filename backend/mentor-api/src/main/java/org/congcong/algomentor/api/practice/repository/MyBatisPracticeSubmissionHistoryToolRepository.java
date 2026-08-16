package org.congcong.algomentor.api.practice.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.mapper.model.PracticeSubmissionHistoryDetailRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeSubmissionHistoryOverviewRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeSubmissionHistorySubmissionRow;
import org.congcong.algomentor.mentor.application.practice.PracticeSubmissionHistoryDetail;
import org.congcong.algomentor.mentor.application.practice.PracticeSubmissionHistoryOverview;
import org.congcong.algomentor.mentor.application.practice.PracticeSubmissionHistoryPage;
import org.congcong.algomentor.mentor.application.practice.PracticeSubmissionHistoryReview;
import org.congcong.algomentor.mentor.application.practice.PracticeSubmissionHistoryToolContracts;
import org.congcong.algomentor.mentor.application.practice.PracticeSubmissionHistoryToolRepository;

/** PostgreSQL/MyBatis 历史提交 Tool 仓储；每项查询都显式以 userId 作为首个数据范围。 */
public class MyBatisPracticeSubmissionHistoryToolRepository implements PracticeSubmissionHistoryToolRepository {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
  };
  private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
  };

  private final PracticeCodeReviewMapper mapper;
  private final ObjectMapper objectMapper;

  public MyBatisPracticeSubmissionHistoryToolRepository(PracticeCodeReviewMapper mapper, ObjectMapper objectMapper) {
    this.mapper = java.util.Objects.requireNonNull(mapper, "mapper must not be null");
    this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper must not be null");
  }

  @Override
  public Optional<PracticeSubmissionHistoryOverview> findOverview(long userId, String problemSlug) {
    if (!valid(userId, problemSlug)) {
      return Optional.empty();
    }
    PracticeSubmissionHistoryOverviewRow row = mapper.findSubmissionHistoryOverview(userId, problemSlug.trim());
    if (row == null) {
      return Optional.empty();
    }
    return Optional.of(new PracticeSubmissionHistoryOverview(
        row.formalSubmissionCount(),
        row.passedSubmissionCount(),
        row.firstSubmittedAt(),
        toReview(row.reviewId(), row.submittedAt(), row.language(), row.totalScore(), row.passed(),
            row.correctnessScore(), row.complexityScore(), row.edgeCaseScore(), row.codeQualityScore(),
            row.problemFitScore(), row.deductionReasonsJson(), row.improvementSuggestionsJson(),
            row.affectedTagIdsJson(), row.reviewHistorySummary())));
  }

  @Override
  public PracticeSubmissionHistoryPage findSubmissions(
      long userId,
      String problemSlug,
      Instant afterCreatedAt,
      Long afterReviewId,
      int limit
  ) {
    if (!valid(userId, problemSlug) || limit < 1 || limit > PracticeSubmissionHistoryToolContracts.MAX_LIST_LIMIT
        || (afterCreatedAt == null) != (afterReviewId == null) || afterReviewId != null && afterReviewId < 1) {
      return new PracticeSubmissionHistoryPage(List.of(), false);
    }
    List<PracticeSubmissionHistorySubmissionRow> rows = mapper.findSubmissionHistorySubmissions(
        userId, problemSlug.trim(), afterCreatedAt, afterReviewId, limit + 1);
    boolean hasMore = rows.size() > limit;
    List<PracticeSubmissionHistoryReview> submissions = rows.stream()
        .limit(limit)
        .map(this::toReview)
        .toList();
    return new PracticeSubmissionHistoryPage(submissions, hasMore);
  }

  @Override
  public Optional<PracticeSubmissionHistoryDetail> findSubmissionDetail(long userId, String problemSlug, long reviewId) {
    if (!valid(userId, problemSlug) || reviewId < 1) {
      return Optional.empty();
    }
    PracticeSubmissionHistoryDetailRow row = mapper.findSubmissionHistoryDetail(userId, problemSlug.trim(), reviewId);
    if (row == null) {
      return Optional.empty();
    }
    return Optional.of(new PracticeSubmissionHistoryDetail(toReview(row), row.normalizedCode()));
  }

  private PracticeSubmissionHistoryReview toReview(PracticeSubmissionHistorySubmissionRow row) {
    return toReview(row.reviewId(), row.submittedAt(), row.language(), row.totalScore(), row.passed(),
        row.correctnessScore(), row.complexityScore(), row.edgeCaseScore(), row.codeQualityScore(), row.problemFitScore(),
        row.deductionReasonsJson(), row.improvementSuggestionsJson(), row.affectedTagIdsJson(), row.reviewHistorySummary());
  }

  private PracticeSubmissionHistoryReview toReview(PracticeSubmissionHistoryDetailRow row) {
    return toReview(row.reviewId(), row.submittedAt(), row.language(), row.totalScore(), row.passed(),
        row.correctnessScore(), row.complexityScore(), row.edgeCaseScore(), row.codeQualityScore(), row.problemFitScore(),
        row.deductionReasonsJson(), row.improvementSuggestionsJson(), row.affectedTagIdsJson(), row.reviewHistorySummary());
  }

  private PracticeSubmissionHistoryReview toReview(
      long reviewId,
      Instant submittedAt,
      String language,
      BigDecimal totalScore,
      boolean passed,
      BigDecimal correctnessScore,
      BigDecimal complexityScore,
      BigDecimal edgeCaseScore,
      BigDecimal codeQualityScore,
      BigDecimal problemFitScore,
      String deductionReasonsJson,
      String improvementSuggestionsJson,
      String affectedTagIdsJson,
      String reviewHistorySummary
  ) {
    return new PracticeSubmissionHistoryReview(
        reviewId,
        submittedAt,
        language,
        totalScore,
        passed,
        correctnessScore,
        complexityScore,
        edgeCaseScore,
        codeQualityScore,
        problemFitScore,
        read(deductionReasonsJson, STRING_LIST),
        read(improvementSuggestionsJson, STRING_LIST),
        read(affectedTagIdsJson, LONG_LIST),
        reviewHistorySummary);
  }

  private <T> T read(String source, TypeReference<T> type) {
    try {
      return source == null || source.isBlank() ? objectMapper.readValue("[]", type) : objectMapper.readValue(source, type);
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("Practice submission history JSON mapping failed", exception);
    }
  }

  private boolean valid(long userId, String problemSlug) {
    return userId > 0 && problemSlug != null && !problemSlug.isBlank();
  }
}
