package org.congcong.algomentor.api.profile.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.congcong.algomentor.api.profile.mapper.LearnerMemoryMapper;
import org.congcong.algomentor.api.profile.mapper.model.LearnerMemoryUpdateRunReviewRow;
import org.congcong.algomentor.api.profile.mapper.model.LearnerMemoryUpdateRunRow;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRun;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRunDraft;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRunReview;
import org.congcong.algomentor.mentor.application.profile.run.repository.LearnerMemoryUpdateRunRepository;

/** PostgreSQL/MyBatis update run、幂等键和触发 Review 存储适配器。 */
public class MyBatisLearnerMemoryUpdateRunRepository implements LearnerMemoryUpdateRunRepository {

  private static final Comparator<LearnerMemoryUpdateRunReview> REVIEW_ORDER = Comparator
      .comparingInt(LearnerMemoryUpdateRunReview::sequenceNo)
      .thenComparingLong(LearnerMemoryUpdateRunReview::reviewId);

  private final LearnerMemoryMapper mapper;

  public MyBatisLearnerMemoryUpdateRunRepository(LearnerMemoryMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  public Optional<LearnerMemoryUpdateRun> findByIdempotencyKey(String idempotencyKey) {
    return Optional.ofNullable(mapper.findUpdateRunByIdempotencyKey(requireKey(idempotencyKey)))
        .map(this::toUpdateRun);
  }

  @Override
  public Optional<LearnerMemoryUpdateRun> findById(long updateRunId) {
    requirePositive(updateRunId, "update run id");
    return Optional.ofNullable(mapper.findUpdateRunById(updateRunId)).map(this::toUpdateRun);
  }

  @Override
  public LearnerMemoryUpdateRun create(LearnerMemoryUpdateRunDraft draft) {
    LearnerMemoryUpdateRunRow row = mapper.insertUpdateRun(Objects.requireNonNull(draft, "draft"));
    if (row == null) {
      throw new IllegalStateException("Learner memory update run insert did not return a run");
    }
    return toUpdateRun(row);
  }

  @Override
  public void insertTriggerReviews(Collection<LearnerMemoryUpdateRunReview> reviews) {
    List<LearnerMemoryUpdateRunReview> values = orderedReviews(reviews);
    if (!values.isEmpty() && mapper.insertUpdateRunReviews(values) != values.size()) {
      throw new IllegalStateException("Learner memory trigger reviews were not fully inserted");
    }
  }

  @Override
  public List<LearnerMemoryUpdateRunReview> findTriggerReviews(long updateRunId) {
    requirePositive(updateRunId, "update run id");
    return mapper.findUpdateRunReviews(updateRunId).stream()
        .map(row -> new LearnerMemoryUpdateRunReview(row.updateRunId(), row.reviewId(), row.sequenceNo()))
        .toList();
  }

  @Override
  public void bindAgentRun(long updateRunId, long agentRunId) {
    requirePositive(updateRunId, "update run id");
    requirePositive(agentRunId, "agent run id");
    if (mapper.bindAgentRun(updateRunId, agentRunId) != 1) {
      throw new IllegalStateException("Learner memory update run agent was not bound");
    }
  }

  @Override
  public void restartFailed(long updateRunId, Instant restartedAt) {
    requirePositive(updateRunId, "update run id");
    if (restartedAt == null || mapper.restartFailedUpdateRun(updateRunId, restartedAt) != 1) {
      throw new IllegalStateException("Learner memory failed update run was not restarted");
    }
  }

  @Override
  public void complete(
      long updateRunId,
      LearnerMemoryRunContract.Status status,
      int operationCount,
      int toolCallCount,
      String failureCode,
      Instant completedAt) {
    requirePositive(updateRunId, "update run id");
    if (status == null || !status.isTerminal() || operationCount < 0 || toolCallCount < 0 || completedAt == null) {
      throw new IllegalArgumentException("update run completion is invalid");
    }
    if (mapper.completeUpdateRun(
        updateRunId, status, operationCount, toolCallCount, normalizeNullable(failureCode), completedAt) != 1) {
      throw new IllegalStateException("Learner memory running update run was not completed");
    }
  }

  private LearnerMemoryUpdateRun toUpdateRun(LearnerMemoryUpdateRunRow row) {
    return new LearnerMemoryUpdateRun(
        row.id(),
        row.userId(),
        LearnerMemoryRunContract.Trigger.valueOf(row.triggerType()),
        LearnerMemoryRunContract.Status.valueOf(row.status()),
        row.idempotencyKey(),
        row.agentRunId(),
        row.promptVersion(),
        row.schemaVersion(),
        row.inputCount(),
        row.operationCount(),
        row.toolCallCount(),
        row.failureCode(),
        row.startedAt(),
        row.completedAt(),
        row.createdAt());
  }

  private static List<LearnerMemoryUpdateRunReview> orderedReviews(
      Collection<LearnerMemoryUpdateRunReview> reviews) {
    if (reviews == null || reviews.isEmpty()) {
      return List.of();
    }
    if (reviews.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("trigger reviews must not contain null");
    }
    return reviews.stream().sorted(REVIEW_ORDER).toList();
  }

  private static String requireKey(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("idempotency key must not be blank");
    }
    return value.trim();
  }

  private static String normalizeNullable(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private static void requirePositive(long value, String field) {
    if (value <= 0) {
      throw new IllegalArgumentException(field + " must be positive");
    }
  }
}
