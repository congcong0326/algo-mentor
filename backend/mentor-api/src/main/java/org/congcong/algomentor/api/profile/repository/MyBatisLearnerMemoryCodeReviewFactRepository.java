package org.congcong.algomentor.api.profile.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.mapper.model.LearnerMemoryCodeReviewFactRow;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewFact;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewFactRepository;

/** MyBatis 适配器，只映射 consumer 所需的 Review 轻量字段。 */
public class MyBatisLearnerMemoryCodeReviewFactRepository implements LearnerMemoryCodeReviewFactRepository {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
  };
  private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
  };

  private final PracticeCodeReviewMapper mapper;
  private final ObjectMapper objectMapper;

  public MyBatisLearnerMemoryCodeReviewFactRepository(PracticeCodeReviewMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<LearnerMemoryCodeReviewFact> findAllForUser(long userId) {
    if (userId < 1) {
      return List.of();
    }
    return mapper.findAllProfileFactsForUser(userId).stream().map(this::fact).toList();
  }

  @Override
  public List<LearnerMemoryCodeReviewFact> findByReviewIds(long userId, List<Long> reviewIds) {
    return mapper.findProfileFactsByReviewIds(userId, positiveIds(reviewIds)).stream().map(this::fact).toList();
  }

  @Override
  public List<LearnerMemoryCodeReviewFact> findLatestForProblemSlugs(long userId, List<String> problemSlugs) {
    List<String> values = normalizedSlugs(problemSlugs);
    return values.isEmpty() ? List.of() : mapper.findLatestProfileFactsForProblemSlugs(userId, values).stream()
        .map(this::fact).toList();
  }

  @Override
  public List<LearnerMemoryCodeReviewFact> findRecentDistinctProblems(
      long userId, List<String> excludedProblemSlugs, int limit) {
    if (limit < 1) {
      return List.of();
    }
    return mapper.findRecentDistinctProfileFacts(userId, normalizedSlugs(excludedProblemSlugs), limit).stream()
        .map(this::fact).toList();
  }

  private LearnerMemoryCodeReviewFact fact(LearnerMemoryCodeReviewFactRow row) {
    return new LearnerMemoryCodeReviewFact(
        row.reviewId(), row.problemSlug(), row.problemTitle(), row.versionNo(), row.totalScore(), row.correctnessScore(),
        row.complexityScore(), row.edgeCaseScore(), row.codeQualityScore(), row.problemFitScore(), row.passed(),
        read(row.deductionReasonsJson()), read(row.improvementSuggestionsJson()),
        readTagIds(row.affectedTagIdsJson()), row.createdAt());
  }

  private List<String> read(String value) {
    try {
      return value == null || value.isBlank() ? List.of() : objectMapper.readValue(value, STRING_LIST);
    } catch (Exception exception) {
      throw new IllegalStateException("Code review profile fact JSON is invalid", exception);
    }
  }

  private List<Long> readTagIds(String value) {
    try {
      return value == null || value.isBlank() ? List.of() : objectMapper.readValue(value, LONG_LIST);
    } catch (Exception exception) {
      throw new IllegalStateException("Code review profile fact tag JSON is invalid", exception);
    }
  }

  private List<Long> positiveIds(List<Long> ids) {
    return ids == null ? List.of() : ids.stream().filter(id -> id != null && id > 0).distinct().sorted().toList();
  }

  private List<String> normalizedSlugs(List<String> slugs) {
    return slugs == null ? List.of() : slugs.stream().filter(slug -> slug != null && !slug.isBlank())
        .map(String::trim).distinct().sorted().toList();
  }
}
