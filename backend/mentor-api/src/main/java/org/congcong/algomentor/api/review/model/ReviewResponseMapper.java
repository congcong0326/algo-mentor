package org.congcong.algomentor.api.review.model;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;
import org.congcong.algomentor.mentor.application.review.attempt.ProblemReviewAttempt;
import org.congcong.algomentor.mentor.application.review.attempt.ReviewAttemptResult;
import org.congcong.algomentor.mentor.application.review.card.ProblemReviewCard;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardOverview;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardOverviewPage;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardContext;
import org.congcong.algomentor.mentor.application.review.card.ReviewQueue;
import org.congcong.algomentor.mentor.application.review.card.ReviewSummary;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNote;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreference;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsReviewSchedulerService;

public final class ReviewResponseMapper {

  private ReviewResponseMapper() {
  }

  public static ReviewCardResponse toCardResponse(ProblemReviewCard card) {
    Map<String, Object> detail = card.sourceDetail();
    return new ReviewCardResponse(
        card.id(),
        card.problemSlug(),
        text(detail.get(ReviewContractConstants.METADATA_TITLE_CN), card.problemSlug()),
        text(detail.get(ReviewContractConstants.METADATA_DIFFICULTY), null),
        card.source().name(),
        detail,
        card.scheduling().repetitions(),
        card.scheduling().intervalDays(),
        card.scheduling().fsrsState().name(),
        card.scheduling().fsrsStep(),
        card.scheduling().fsrsStability(),
        card.scheduling().fsrsDifficulty(),
        card.dueAt(),
        card.scheduling().lapses(),
        card.lastReviewedAt(),
        card.lastRating() == null ? null : card.lastRating().name(),
        card.archived(),
        card.createdAt(),
        card.updatedAt());
  }

  public static ReviewCardOverviewResponse toCardOverviewResponse(ReviewCardOverview overview) {
    return new ReviewCardOverviewResponse(
        toCardResponse(overview.card()),
        overview.recentCodeReviews().stream().map(entry -> new PracticeCodeReviewIndexEntryResponse(
            entry.reviewId(),
            entry.planId(),
            entry.phaseIndex(),
            entry.problemSlug(),
            entry.practiceSessionId(),
            entry.versionNo(),
            entry.language(),
            entry.contentLocale(),
            entry.totalScore(),
            entry.passed(),
            entry.primaryFeedback(),
            entry.createdAt())).toList());
  }

  public static ReviewCardOverviewPageResponse toCardOverviewPageResponse(ReviewCardOverviewPage page) {
    return new ReviewCardOverviewPageResponse(
        page.items().stream().map(ReviewResponseMapper::toCardOverviewResponse).toList(),
        page.total(),
        page.activeCount(),
        page.mistakeCount(),
        page.page(),
        page.pageSize());
  }

  public static ReviewQueueResponse toQueueResponse(ReviewQueue queue) {
    return new ReviewQueueResponse(queue.items().stream().map(ReviewResponseMapper::toCardResponse).toList(), queue.dueCount());
  }

  public static ReviewSummaryResponse toSummaryResponse(ReviewSummary summary) {
    return new ReviewSummaryResponse(
        summary.dueCount(),
        summary.remainingTodayCount(),
        summary.nextDueAt());
  }

  public static ReviewCardContextResponse toContextResponse(ReviewCardContext context) {
    var problem = context.problem();
    ReviewCardResponse card = toCardResponse(context.card());
    ReviewCardResponse localizedCard = new ReviewCardResponse(
        card.id(),
        card.problemSlug(),
        problem.title(),
        problem.difficulty(),
        card.source(),
        card.sourceDetail(),
        card.repetitions(),
        card.intervalDays(),
        card.fsrsState(),
        card.fsrsStep(),
        card.fsrsStability(),
        card.fsrsDifficulty(),
        card.dueAt(),
        card.lapses(),
        card.lastReviewedAt(),
        card.lastRating(),
        card.archived(),
        card.createdAt(),
        card.updatedAt());
    return new ReviewCardContextResponse(
        localizedCard,
        new ReviewProblemResponse(
            problem.slug(),
            problem.title(),
            problem.difficulty(),
            problem.fullStatementMarkdown()),
        toNoteResponse(context.note()),
        context.recentAttempts().stream().map(attempt -> toAttemptResponse(attempt, false)).toList(),
        context.intervalPreviews().stream().map(ReviewResponseMapper::toIntervalResponse).toList());
  }

  public static UserProblemNoteResponse toNoteResponse(UserProblemNote note) {
    return new UserProblemNoteResponse(
        note.id(),
        note.problemSlug(),
        note.outline(),
        note.noteMarkdown(),
        note.revision(),
        note.coachSummaryRevision(),
        note.exists(),
        note.hasContent(),
        note.createdAt(),
        note.coachSummaryUpdatedAt(),
        note.updatedAt());
  }

  public static ReviewAttemptResponse toAttemptResponse(ReviewAttemptResult result) {
    return toAttemptResponse(result.attempt(), result.duplicate());
  }

  public static ReviewAttemptResponse toAttemptResponse(ProblemReviewAttempt attempt, boolean duplicate) {
    return new ReviewAttemptResponse(
        attempt.id(),
        attempt.reviewCardId(),
        attempt.clientAttemptId(),
        attempt.rating().name(),
        attempt.schedulingBefore(),
        attempt.schedulingAfter(),
        attempt.reviewedAt(),
        duplicate);
  }

  public static List<ReviewAttemptResponse> toAttemptResponses(List<ProblemReviewAttempt> attempts) {
    return attempts.stream().map(attempt -> toAttemptResponse(attempt, false)).toList();
  }

  public static ReviewPreferenceResponse toPreferenceResponse(ReviewPreference preference) {
    return new ReviewPreferenceResponse(
        preference.desiredRetention(),
        preference.dailyNewLimit(),
        preference.dailyLearningLimit(),
        preference.dailyReviewLimit(),
        preference.maximumIntervalDays(),
        preference.enableFuzzing());
  }

  private static ReviewIntervalPreviewResponse toIntervalResponse(
      FsrsReviewSchedulerService.ReviewIntervalPreview preview
  ) {
    return new ReviewIntervalPreviewResponse(
        preview.rating().name(),
        preview.dueAt(),
        preview.intervalDays());
  }

  private static String text(Object value, String fallback) {
    if (value == null || value.toString().isBlank()) {
      return fallback;
    }
    return value.toString();
  }
}
