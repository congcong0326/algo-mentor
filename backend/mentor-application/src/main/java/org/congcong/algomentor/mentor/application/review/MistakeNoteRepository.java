package org.congcong.algomentor.mentor.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;

public interface MistakeNoteRepository {

  MistakeNote upsertForReviewFailure(PracticeCodeReview review, JsonNode sourceDetail);

  MistakeNote mark(long userId, String problemSlug, MistakeSource source, JsonNode sourceDetail, Instant now);

  Optional<MistakeNote> findById(long noteId);

  Optional<MistakeNote> findForUser(long userId, long noteId);

  List<MistakeNote> findDue(long userId, Instant now, int limit);

  List<MistakeNote> list(long userId, MasteryState state, MistakeSource source, String keyword, int limit, int offset);

  int countDue(long userId, Instant now);

  MistakeNote updateArchived(long userId, long noteId, boolean archived, Instant now);

  MistakeNote updatePersistentNote(long userId, long noteId, String text, Instant now);

  MistakeNote updateScheduling(
      long noteId,
      SchedulingState state,
      Instant dueAt,
      ReviewGrade lastGrade,
      Instant reviewedAt
  );

  void savePendingCard(long noteId, JsonNode cardJson, CardVariant variant, String signature, Instant generatedAt);
}
