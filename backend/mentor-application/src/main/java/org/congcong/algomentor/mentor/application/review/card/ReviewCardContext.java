package org.congcong.algomentor.mentor.application.review.card;

import java.util.List;
import org.congcong.algomentor.mentor.application.review.attempt.ProblemReviewAttempt;
import org.congcong.algomentor.mentor.application.review.catalog.ReviewProblemSnapshot;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNote;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsReviewSchedulerService;

public record ReviewCardContext(
    ProblemReviewCard card,
    ReviewProblemSnapshot problem,
    UserProblemNote note,
    List<ProblemReviewAttempt> recentAttempts,
    List<FsrsReviewSchedulerService.ReviewIntervalPreview> intervalPreviews
) {
  public ReviewCardContext {
    recentAttempts = recentAttempts == null ? List.of() : List.copyOf(recentAttempts);
    intervalPreviews = intervalPreviews == null ? List.of() : List.copyOf(intervalPreviews);
  }
}
