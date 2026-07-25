package org.congcong.algomentor.api.review.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.api.review.mapper.ProblemReviewAttemptMapper;
import org.congcong.algomentor.api.review.mapper.model.ProblemReviewAttemptRow;
import org.congcong.algomentor.mentor.application.review.attempt.ProblemReviewAttempt;
import org.congcong.algomentor.mentor.application.review.attempt.ReviewAttemptRepository;
import org.congcong.algomentor.mentor.application.review.attempt.ReviewSchedulingSnapshot;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewRating;

public class MyBatisReviewAttemptRepository implements ReviewAttemptRepository {

  private final ProblemReviewAttemptMapper mapper;
  private final ObjectMapper objectMapper;

  public MyBatisReviewAttemptRepository(ProblemReviewAttemptMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<ProblemReviewAttempt> findByUserAndClientAttemptId(long userId, UUID clientAttemptId) {
    return Optional.ofNullable(mapper.findByUserAndClientAttemptId(userId, clientAttemptId.toString()))
        .map(this::toAttempt);
  }

  @Override
  public Optional<ProblemReviewAttempt> insertIfAbsent(ProblemReviewAttempt attempt) {
    return Optional.ofNullable(mapper.insertIfAbsent(
        attempt.reviewCardId(),
        attempt.userId(),
        attempt.clientAttemptId().toString(),
        attempt.rating().name(),
        objectMapper.valueToTree(attempt.schedulingBefore()),
        objectMapper.valueToTree(attempt.schedulingAfter()),
        attempt.reviewedAt())).map(this::toAttempt);
  }

  @Override
  public List<ProblemReviewAttempt> findRecent(long userId, long reviewCardId, int limit) {
    return mapper.findRecent(userId, reviewCardId, limit).stream().map(this::toAttempt).toList();
  }

  private ProblemReviewAttempt toAttempt(ProblemReviewAttemptRow row) {
    return new ProblemReviewAttempt(
        row.id(),
        row.reviewCardId(),
        row.userId(),
        UUID.fromString(row.clientAttemptId()),
        ReviewRating.valueOf(row.rating()),
        objectMapper.convertValue(row.schedulingBeforeJson(), ReviewSchedulingSnapshot.class),
        objectMapper.convertValue(row.schedulingAfterJson(), ReviewSchedulingSnapshot.class),
        row.reviewedAt());
  }
}
