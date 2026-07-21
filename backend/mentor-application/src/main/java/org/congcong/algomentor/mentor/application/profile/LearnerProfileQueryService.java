package org.congcong.algomentor.mentor.application.profile;

import java.util.Collection;
import java.util.List;

/** ACTIVE 画像查询门面，默认不暴露历史或 SUPPRESSED 条目。 */
public class LearnerProfileQueryService {

  private final LearnerProfileRepository repository;

  public LearnerProfileQueryService(LearnerProfileRepository repository) {
    this.repository = repository;
  }

  public LearnerProfileSnapshot snapshot(LearnerProfileIdentity identity) {
    return LearnerProfileSnapshot.from(identity, repository.findCurrent(identity).orElse(null));
  }

  public List<LearnerProfileEntry> findCurrentByDimensions(
      long userId, LearnerProfileEntryKind kind, Collection<LearnerProfileDimension> dimensions) {
    return repository.findCurrentByDimensions(userId, kind, dimensions);
  }

  public List<LearnerProfileEntry> findCurrentByTagIds(long userId, Collection<Long> tagIds) {
    return repository.findCurrentByTagIds(userId, tagIds);
  }
}
