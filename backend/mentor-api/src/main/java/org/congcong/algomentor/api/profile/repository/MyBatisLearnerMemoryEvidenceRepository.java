package org.congcong.algomentor.api.profile.repository;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.api.profile.mapper.LearnerMemoryMapper;
import org.congcong.algomentor.api.profile.mapper.model.LearnerMemoryMessageEvidenceRow;
import org.congcong.algomentor.api.profile.mapper.model.LearnerMemoryReviewEvidenceRow;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimMessageEvidence;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimReviewEvidence;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.evidence.repository.LearnerMemoryEvidenceRepository;

/** PostgreSQL/MyBatis claim evidence 存储适配器。 */
public class MyBatisLearnerMemoryEvidenceRepository implements LearnerMemoryEvidenceRepository {

  private final LearnerMemoryMapper mapper;

  public MyBatisLearnerMemoryEvidenceRepository(LearnerMemoryMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  public List<LearnerMemoryClaimReviewEvidence> findReviewEvidenceByRevisionIds(
      long userId, Collection<Long> revisionIds) {
    requirePositive(userId, "user id");
    List<Long> values = positiveIds(revisionIds);
    return values.isEmpty() ? List.of() : mapper.findReviewEvidenceByRevisionIds(userId, values).stream()
        .map(this::toReviewEvidence)
        .toList();
  }

  @Override
  public List<LearnerMemoryClaimMessageEvidence> findMessageEvidenceByRevisionIds(
      long userId, Collection<Long> revisionIds) {
    requirePositive(userId, "user id");
    List<Long> values = positiveIds(revisionIds);
    return values.isEmpty() ? List.of() : mapper.findMessageEvidenceByRevisionIds(userId, values).stream()
        .map(this::toMessageEvidence)
        .toList();
  }

  @Override
  public Set<Long> findOwnedReviewIds(long userId, Collection<Long> reviewIds) {
    requirePositive(userId, "user id");
    List<Long> values = positiveIds(reviewIds);
    return values.isEmpty() ? Set.of() : Set.copyOf(new LinkedHashSet<>(mapper.findOwnedReviewIds(userId, values)));
  }

  @Override
  public Set<Long> findOwnedMessageIds(long userId, Collection<Long> messageIds) {
    requirePositive(userId, "user id");
    List<Long> values = positiveIds(messageIds);
    return values.isEmpty() ? Set.of() : Set.copyOf(new LinkedHashSet<>(mapper.findOwnedMessageIds(userId, values)));
  }

  @Override
  public void insertReviewEvidence(Collection<LearnerMemoryClaimReviewEvidence> evidence) {
    List<LearnerMemoryClaimReviewEvidence> values = immutableValues(evidence, "review evidence");
    if (!values.isEmpty() && mapper.insertReviewEvidence(values) != values.size()) {
      throw new IllegalStateException("Learner memory review evidence was not fully inserted");
    }
  }

  @Override
  public void insertMessageEvidence(Collection<LearnerMemoryClaimMessageEvidence> evidence) {
    List<LearnerMemoryClaimMessageEvidence> values = immutableValues(evidence, "message evidence");
    if (!values.isEmpty() && mapper.insertMessageEvidence(values) != values.size()) {
      throw new IllegalStateException("Learner memory message evidence was not fully inserted");
    }
  }

  private LearnerMemoryClaimReviewEvidence toReviewEvidence(LearnerMemoryReviewEvidenceRow row) {
    return new LearnerMemoryClaimReviewEvidence(
        row.claimRevisionId(),
        row.reviewId(),
        LearnerMemoryEvidenceContract.ReviewRole.valueOf(row.evidenceRole()),
        row.sequenceNo(),
        row.createdAt());
  }

  private LearnerMemoryClaimMessageEvidence toMessageEvidence(LearnerMemoryMessageEvidenceRow row) {
    return new LearnerMemoryClaimMessageEvidence(
        row.claimRevisionId(),
        row.messageId(),
        LearnerMemoryEvidenceContract.MessageRole.valueOf(row.evidenceRole()),
        row.sequenceNo(),
        row.createdAt());
  }

  private static <T> List<T> immutableValues(Collection<T> values, String field) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    if (values.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(field + " must not contain null");
    }
    return List.copyOf(values);
  }

  private static List<Long> positiveIds(Collection<Long> values) {
    return values == null ? List.of() : values.stream()
        .filter(value -> value != null && value > 0)
        .distinct()
        .sorted()
        .toList();
  }

  private static void requirePositive(long value, String field) {
    if (value <= 0) {
      throw new IllegalArgumentException(field + " must be positive");
    }
  }
}
