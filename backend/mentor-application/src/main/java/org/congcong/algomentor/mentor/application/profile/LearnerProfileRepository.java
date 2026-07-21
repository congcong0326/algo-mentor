package org.congcong.algomentor.mentor.application.profile;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 学习者画像版本存储端口。 */
public interface LearnerProfileRepository {

  Optional<LearnerProfileEntry> findCurrent(LearnerProfileIdentity identity);

  void lockUser(long userId);

  Optional<LearnerProfileEntry> findCurrentForUpdate(LearnerProfileIdentity identity);

  List<LearnerProfileEntry> findHistory(LearnerProfileIdentity identity);

  List<LearnerProfileEntry> findCurrentByDimensions(
      long userId,
      LearnerProfileEntryKind entryKind,
      Collection<LearnerProfileDimension> dimensions);

  List<LearnerProfileEntry> findCurrentByTagIds(long userId, Collection<Long> tagIds);

  LearnerProfileEntry insert(LearnerProfileEntryDraft draft);

  void markInactive(long entryId, LearnerProfileEntryStatus status, Instant validTo);

  void deleteIdentity(LearnerProfileIdentity identity);
}
