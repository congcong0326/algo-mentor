package org.congcong.algomentor.mentor.application.review;

import java.time.Instant;

public record RecallReviewResult(
    RecallJudgment judgment,
    Instant nextDueAt,
    SchedulingState scheduling
) {
}
