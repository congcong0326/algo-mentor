package org.congcong.algomentor.api.review.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.review.mapper.model.MistakeNoteRow;
import org.congcong.algomentor.api.review.mapper.model.MistakeNoteUpsertRow;

@Mapper
public interface MistakeNoteMapper {

  MistakeNoteRow upsertForReview(MistakeNoteUpsertRow row);

  MistakeNoteRow mark(MistakeNoteUpsertRow row);

  MistakeNoteRow findById(@Param("noteId") long noteId);

  MistakeNoteRow findForUser(@Param("userId") long userId, @Param("noteId") long noteId);

  MistakeNoteRow findByUserAndSlug(@Param("userId") long userId, @Param("problemSlug") String problemSlug);

  List<MistakeNoteRow> findDue(
      @Param("userId") long userId,
      @Param("now") Instant now,
      @Param("limit") int limit
  );

  List<MistakeNoteRow> list(
      @Param("userId") long userId,
      @Param("source") String source,
      @Param("mistakeOnly") boolean mistakeOnly,
      @Param("keyword") String keyword,
      @Param("limit") int limit,
      @Param("offset") int offset
  );

  int countDue(@Param("userId") long userId, @Param("now") Instant now);

  MistakeNoteRow updateArchived(
      @Param("userId") long userId,
      @Param("noteId") long noteId,
      @Param("archived") boolean archived,
      @Param("now") Instant now
  );

  MistakeNoteRow updatePersistentNote(
      @Param("userId") long userId,
      @Param("noteId") long noteId,
      @Param("text") String text,
      @Param("now") Instant now
  );

  MistakeNoteRow updateScheduling(
      @Param("noteId") long noteId,
      @Param("repetitions") int repetitions,
      @Param("intervalDays") int intervalDays,
      @Param("fsrsState") String fsrsState,
      @Param("fsrsStep") Integer fsrsStep,
      @Param("fsrsStability") BigDecimal fsrsStability,
      @Param("fsrsDifficulty") BigDecimal fsrsDifficulty,
      @Param("lapses") int lapses,
      @Param("dueAt") Instant dueAt,
      @Param("lastRating") String lastRating,
      @Param("reviewedAt") Instant reviewedAt
  );

  int savePendingCard(
      @Param("noteId") long noteId,
      @Param("cardJson") JsonNode cardJson,
      @Param("variant") String variant,
      @Param("signature") String signature,
      @Param("generatedAt") Instant generatedAt
  );
}
