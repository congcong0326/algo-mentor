package org.congcong.algomentor.api.review.model;

import java.util.List;
import java.util.Locale;
import org.congcong.algomentor.api.review.service.MistakeNoteDisplayInfoResolver;
import org.congcong.algomentor.mentor.application.review.FsrsReviewSchedulerService;
import org.congcong.algomentor.mentor.application.review.MistakeNote;
import org.congcong.algomentor.mentor.application.review.RecallConfirmResult;
import org.congcong.algomentor.mentor.application.review.RecallReviewResult;
import org.congcong.algomentor.mentor.application.review.ReviewPreference;
import org.congcong.algomentor.mentor.application.review.ReviewRecallEvaluationResult;
import org.congcong.algomentor.mentor.application.review.ReviewCard;
import org.congcong.algomentor.mentor.application.review.ReviewCardDetail;
import org.congcong.algomentor.mentor.application.review.ReviewQueue;
import org.congcong.algomentor.mentor.application.review.ReviewSummary;

public final class MistakeReviewResponseMapper {

  private MistakeReviewResponseMapper() {
  }

  public static MistakeNoteResponse toNoteResponse(MistakeNote note) {
    return toNoteResponse(note, new MistakeNoteDisplayInfo(note.problemSlug(), null, null));
  }

  public static MistakeNoteResponse toNoteResponse(MistakeNote note, MistakeNoteDisplayInfo displayInfo) {
    return new MistakeNoteResponse(
        note.id(),
        note.problemSlug(),
        displayInfo.problemTitle(),
        displayInfo.problemLocale(),
        displayInfo.problemDifficulty(),
        note.source().name(),
        note.sourceDetail(),
        note.scheduling().repetitions(),
        note.scheduling().intervalDays(),
        note.scheduling().fsrsState(),
        note.scheduling().fsrsStep(),
        note.scheduling().fsrsStability(),
        note.scheduling().fsrsDifficulty(),
        note.dueAt(),
        note.scheduling().lapses(),
        note.lastReviewedAt(),
        note.lastRating() == null ? null : note.lastRating().name(),
        note.archived(),
        note.userNotePersistent(),
        note.createdAt(),
        note.updatedAt());
  }

  public static ReviewCardResponse toCardResponse(ReviewCard card) {
    return toCardResponse(new ReviewCardDetail(card, null, List.of()));
  }

  public static ReviewCardResponse toCardResponse(ReviewCardDetail detail) {
    ReviewCard card = detail.card();
    return new ReviewCardResponse(
        card.cardVariant().name(),
        new ProblemRefResponse(
            card.problemRef().slug(),
            card.problemRef().titleCn(),
            card.problemRef().difficulty()),
        card.problemStatement() == null ? null : new ReviewProblemStatementSummaryResponse(
            card.problemStatement().summary(),
            card.problemStatement().hasFullContent()),
        card.contextSummary(),
        card.prompts().stream()
            .map(prompt -> new ReviewCardPromptResponse(prompt.key(), prompt.label(), prompt.hint()))
            .toList(),
        card.scaffold() == null ? null : new ReviewCardScaffoldResponse(
            card.scaffold().templateMarkdown(),
            card.scaffold().maxInputChars()),
        card.revealPolicy(),
        card.expectedEffort(),
        detail.userNotePersistent(),
        detail.recentRecallHistory().stream()
            .map(item -> new ReviewRecallHistoryResponse(
                item.id(),
                item.rating().name(),
                item.userRecallText(),
                item.userNoteTransient(),
                item.reviewedAt(),
                item.intervalAfter()))
            .toList());
  }

  public static RecallReviewResponse toRecallResponse(RecallReviewResult result) {
    return new RecallReviewResponse(
        result.judgment().suggestedRating().name(),
        result.judgment().hitPoints(),
        result.judgment().missedPoints(),
        result.judgment().gapSummary(),
        result.nextDueAt(),
        result.scheduling().intervalDays(),
        result.scheduling().repetitions());
  }

  public static ReviewQueueResponse toQueueResponse(ReviewQueue queue) {
    return new ReviewQueueResponse(
        queue.items().stream().map(MistakeReviewResponseMapper::toNoteResponse).toList(),
        queue.dueCount());
  }

  public static ReviewQueueResponse toQueueResponse(
      ReviewQueue queue,
      MistakeNoteDisplayInfoResolver displayInfoResolver,
      Locale fallbackLocale
  ) {
    return new ReviewQueueResponse(
        queue.items().stream()
            .map(note -> toNoteResponse(note, displayInfoResolver.resolve(note, fallbackLocale)))
            .toList(),
        queue.dueCount());
  }

  public static ReviewSummaryResponse toSummaryResponse(ReviewSummary summary) {
    return new ReviewSummaryResponse(summary.dueCount());
  }

  public static RecallEvaluationResponse toEvaluationResponse(ReviewRecallEvaluationResult result) {
    return new RecallEvaluationResponse(
        result.evaluation().id(),
        result.evaluation().suggestedRating() == null ? null : result.evaluation().suggestedRating().name(),
        result.evaluation().hitPoints(),
        result.evaluation().missedPoints(),
        result.evaluation().gapSummary(),
        result.evaluation().aiSuggested(),
        result.evaluation().createdAt(),
        toIntervalPreviewResponses(result.intervals()));
  }

  public static List<ReviewIntervalPreviewResponse> toIntervalPreviewResponses(
      List<FsrsReviewSchedulerService.ReviewIntervalPreview> intervals
  ) {
    return intervals.stream()
        .map(interval -> new ReviewIntervalPreviewResponse(
            interval.rating().name(),
            interval.dueAt(),
            interval.intervalDays()))
        .toList();
  }

  public static RecallConfirmResponse toConfirmResponse(RecallConfirmResult result) {
    return new RecallConfirmResponse(
        result.rating().name(),
        result.suggestedRating() == null ? null : result.suggestedRating().name(),
        result.nextDueAt(),
        result.scheduling().intervalDays(),
        result.scheduling().repetitions(),
        result.aiSuggested());
  }

  public static ReviewPreferenceResponse toPreferenceResponse(ReviewPreference preference) {
    return new ReviewPreferenceResponse(
        preference.desiredRetention(),
        preference.dailyNewLimit(),
        preference.dailyLearningLimit(),
        preference.dailyReviewLimit(),
        preference.aiSuggestionEnabled());
  }

  public static List<MistakeNoteResponse> toNoteResponses(List<MistakeNote> notes) {
    return notes.stream().map(MistakeReviewResponseMapper::toNoteResponse).toList();
  }

  public static List<MistakeNoteResponse> toNoteResponses(
      List<MistakeNote> notes,
      MistakeNoteDisplayInfoResolver displayInfoResolver,
      Locale fallbackLocale
  ) {
    return notes.stream()
        .map(note -> toNoteResponse(note, displayInfoResolver.resolve(note, fallbackLocale)))
        .toList();
  }
}
