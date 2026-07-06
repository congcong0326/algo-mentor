package org.congcong.algomentor.mentor.application.review;

import java.time.Instant;
import java.util.Map;

public record MistakeNote(
    long id,
    long userId,
    String problemSlug,
    MistakeSource source,
    Map<String, Object> sourceDetail,
    Long originPlanId,
    Integer originPhaseIndex,
    Long originPracticeSessionId,
    SchedulingState scheduling,
    Instant dueAt,
    Instant lastReviewedAt,
    ReviewGrade lastGrade,
    ReviewRating lastRating,
    boolean archived,
    String userNotePersistent,
    ReviewCardCache pendingCard,
    Instant createdAt,
    Instant updatedAt
) {
  public MistakeNote(
      long id,
      long userId,
      String problemSlug,
      MistakeSource source,
      Map<String, Object> sourceDetail,
      Long originPlanId,
      Integer originPhaseIndex,
      Long originPracticeSessionId,
      SchedulingState scheduling,
      Instant dueAt,
      Instant lastReviewedAt,
      ReviewGrade lastGrade,
      boolean archived,
      String userNotePersistent,
      ReviewCardCache pendingCard,
      Instant createdAt,
      Instant updatedAt
  ) {
    this(
        id,
        userId,
        problemSlug,
        source,
        sourceDetail,
        originPlanId,
        originPhaseIndex,
        originPracticeSessionId,
        scheduling,
        dueAt,
        lastReviewedAt,
        lastGrade,
        lastGrade == null ? null : ReviewRating.fromGrade(lastGrade),
        archived,
        userNotePersistent,
        pendingCard,
        createdAt,
        updatedAt);
  }

  public MistakeNote {
    sourceDetail = sourceDetail == null ? Map.of() : Map.copyOf(sourceDetail);
  }
}
