package org.congcong.algomentor.api.profile.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.profile.mapper.model.LearnerProfileEntryRow;
import org.congcong.algomentor.api.profile.mapper.model.LearnerProfileViewRow;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryDraft;

public interface LearnerProfileMapper {

  LearnerProfileEntryRow findCurrent(
      @Param("userId") long userId,
      @Param("entryKind") String entryKind,
      @Param("dimension") String dimension,
      @Param("tagId") Long tagId);

  long lockUser(@Param("userId") long userId);

  LearnerProfileEntryRow findCurrentForUpdate(
      @Param("userId") long userId,
      @Param("entryKind") String entryKind,
      @Param("dimension") String dimension,
      @Param("tagId") Long tagId);

  List<LearnerProfileEntryRow> findHistory(
      @Param("userId") long userId,
      @Param("entryKind") String entryKind,
      @Param("dimension") String dimension,
      @Param("tagId") Long tagId);

  List<LearnerProfileEntryRow> findCurrentByDimensions(
      @Param("userId") long userId,
      @Param("entryKind") String entryKind,
      @Param("dimensions") List<String> dimensions);

  List<LearnerProfileEntryRow> findCurrentByTagIds(
      @Param("userId") long userId,
      @Param("tagIds") List<Long> tagIds);

  List<LearnerProfileViewRow> findCurrentForDisplay(@Param("userId") long userId);

  LearnerProfileEntryRow insert(@Param("draft") LearnerProfileEntryDraft draft);

  int markInactive(
      @Param("entryId") long entryId,
      @Param("status") String status,
      @Param("validTo") Instant validTo);

  int deleteByIdentity(
      @Param("userId") long userId,
      @Param("entryKind") String entryKind,
      @Param("dimension") String dimension,
      @Param("tagId") Long tagId);
}
