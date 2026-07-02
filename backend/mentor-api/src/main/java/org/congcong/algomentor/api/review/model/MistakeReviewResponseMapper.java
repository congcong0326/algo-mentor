package org.congcong.algomentor.api.review.model;

import java.util.List;
import org.congcong.algomentor.mentor.application.review.MistakeNote;
import org.congcong.algomentor.mentor.application.review.RecallReviewResult;
import org.congcong.algomentor.mentor.application.review.ReviewCard;
import org.congcong.algomentor.mentor.application.review.ReviewQueue;
import org.congcong.algomentor.mentor.application.review.ReviewSummary;

public final class MistakeReviewResponseMapper {

  private MistakeReviewResponseMapper() {
  }

  public static MistakeNoteResponse toNoteResponse(MistakeNote note) {
    return new MistakeNoteResponse(
        note.id(),
        note.problemSlug(),
        note.source().name(),
        note.sourceDetail(),
        note.scheduling().masteryState().name(),
        note.scheduling().repetitions(),
        note.scheduling().easeFactor(),
        note.scheduling().intervalDays(),
        note.dueAt(),
        note.scheduling().lapses(),
        note.lastReviewedAt(),
        note.lastGrade() == null ? null : note.lastGrade().name(),
        note.archived(),
        note.userNotePersistent(),
        note.createdAt(),
        note.updatedAt());
  }

  public static ReviewCardResponse toCardResponse(ReviewCard card) {
    return new ReviewCardResponse(
        card.cardVariant().name(),
        new ProblemRefResponse(
            card.problemRef().slug(),
            card.problemRef().titleCn(),
            card.problemRef().difficulty()),
        card.contextSummary(),
        card.prompts().stream()
            .map(prompt -> new ReviewCardPromptResponse(prompt.key(), prompt.label(), prompt.hint()))
            .toList(),
        card.scaffold() == null ? null : new ReviewCardScaffoldResponse(
            card.scaffold().templateMarkdown(),
            card.scaffold().maxInputChars()),
        card.revealPolicy(),
        card.expectedEffort());
  }

  public static RecallReviewResponse toRecallResponse(RecallReviewResult result) {
    return new RecallReviewResponse(
        result.judgment().grade().name(),
        result.judgment().hitPoints(),
        result.judgment().missedPoints(),
        result.judgment().gapSummary(),
        result.nextDueAt(),
        result.scheduling().masteryState().name(),
        result.scheduling().intervalDays(),
        result.scheduling().repetitions());
  }

  public static ReviewQueueResponse toQueueResponse(ReviewQueue queue) {
    return new ReviewQueueResponse(
        queue.items().stream().map(MistakeReviewResponseMapper::toNoteResponse).toList(),
        queue.dueCount());
  }

  public static ReviewSummaryResponse toSummaryResponse(ReviewSummary summary) {
    return new ReviewSummaryResponse(summary.dueCount());
  }

  public static List<MistakeNoteResponse> toNoteResponses(List<MistakeNote> notes) {
    return notes.stream().map(MistakeReviewResponseMapper::toNoteResponse).toList();
  }
}
