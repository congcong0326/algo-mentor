package org.congcong.algomentor.api.profile.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.mapper.model.CodeReviewEvidenceDetailRow;
import org.congcong.algomentor.api.practice.mapper.model.CodeReviewHistoryRow;
import org.congcong.algomentor.api.practice.mapper.model.CodeReviewSubmissionVersionRow;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewEvidence;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewEvidenceDetail;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistory;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistoryRepository;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewSubmissionVersion;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification;

/** MyBatis 适配器：所有 Review 查询都在 SQL 中以用户 ID 作为范围条件。 */
public class MyBatisCodeReviewHistoryRepository implements CodeReviewHistoryRepository {

  private static final int MAX_HISTORY_LIMIT = 5;
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
  };
  private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
  };
  private static final TypeReference<List<PracticeCodeReviewEvidence>> EVIDENCE_LIST = new TypeReference<>() {
  };

  private final PracticeCodeReviewMapper mapper;
  private final ObjectMapper objectMapper;

  public MyBatisCodeReviewHistoryRepository(PracticeCodeReviewMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<CodeReviewHistory> findLatestForProblem(long userId, String problemSlug, int limit) {
    if (userId < 1 || problemSlug == null || problemSlug.isBlank() || limit < 1) {
      return List.of();
    }
    return mapper.findLatestHistoryForProblem(userId, problemSlug.trim(), Math.min(limit, MAX_HISTORY_LIMIT)).stream()
        .map(this::history)
        .toList();
  }

  @Override
  public Optional<CodeReviewEvidenceDetail> findEvidenceDetail(long userId, long reviewId) {
    if (userId < 1 || reviewId < 1) {
      return Optional.empty();
    }
    CodeReviewEvidenceDetailRow row = mapper.findEvidenceDetail(userId, reviewId);
    return row == null ? Optional.empty() : Optional.of(detail(row));
  }

  @Override
  public List<CodeReviewSubmissionVersion> findNormalizedSubmissionVersions(long userId, List<Long> reviewIds) {
    List<Long> ids = positiveIds(reviewIds);
    if (userId < 1 || ids.size() != 2) {
      return List.of();
    }
    return mapper.findNormalizedSubmissionVersions(userId, ids).stream()
        .map(row -> new CodeReviewSubmissionVersion(
            row.reviewId(), row.problemSlug(), row.versionNo(), row.normalizedCode()))
        .toList();
  }

  @Override
  public List<CodeReviewVerification> verifyReviews(long userId, List<Long> reviewIds) {
    List<Long> ids = positiveIds(reviewIds);
    if (userId < 1 || ids.isEmpty()) {
      return List.of();
    }
    return mapper.verifyHistoryReviews(userId, ids).stream()
        .map(row -> new CodeReviewVerification(
            row.reviewId(), row.problemSlug(), row.versionNo(), row.passed(), readTagIds(row.affectedTagIdsJson()), row.createdAt()))
        .toList();
  }

  private CodeReviewEvidenceDetail detail(CodeReviewEvidenceDetailRow row) {
    CodeReviewHistory review = new CodeReviewHistory(
        row.reviewId(), row.problemSlug(), row.versionNo(), score(row), row.passed(),
        readStrings(row.deductionReasonsJson()), readStrings(row.improvementSuggestionsJson()),
        readTagIds(row.affectedTagIdsJson()), row.createdAt());
    return new CodeReviewEvidenceDetail(review, readEvidence(row.detectionEvidenceJson()), row.contextSummary());
  }

  private CodeReviewHistory history(CodeReviewHistoryRow row) {
    return new CodeReviewHistory(
        row.reviewId(), row.problemSlug(), row.versionNo(), score(row), row.passed(),
        readStrings(row.deductionReasonsJson()), readStrings(row.improvementSuggestionsJson()),
        readTagIds(row.affectedTagIdsJson()), row.createdAt());
  }

  private PracticeCodeReviewScore score(CodeReviewHistoryRow row) {
    return new PracticeCodeReviewScore(
        row.correctnessScore(), row.complexityScore(), row.edgeCaseScore(), row.codeQualityScore(),
        row.problemFitScore(), row.totalScore());
  }

  private PracticeCodeReviewScore score(CodeReviewEvidenceDetailRow row) {
    return new PracticeCodeReviewScore(
        row.correctnessScore(), row.complexityScore(), row.edgeCaseScore(), row.codeQualityScore(),
        row.problemFitScore(), row.totalScore());
  }

  private List<String> readStrings(String json) {
    return read(json, STRING_LIST);
  }

  private List<Long> readTagIds(String json) {
    return read(json, LONG_LIST);
  }

  private List<PracticeCodeReviewEvidence> readEvidence(String json) {
    return read(json, EVIDENCE_LIST);
  }

  private <T> List<T> read(String json, TypeReference<List<T>> type) {
    try {
      return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, type);
    } catch (Exception exception) {
      throw new IllegalStateException("Code review history JSON is invalid", exception);
    }
  }

  private static List<Long> positiveIds(List<Long> ids) {
    return ids == null ? List.of() : ids.stream()
        .filter(id -> id != null && id > 0).distinct().sorted().toList();
  }
}
