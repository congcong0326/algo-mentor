package org.congcong.algomentor.api.profile.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.profile.mapper.LearnerProfileMapper;
import org.congcong.algomentor.api.profile.mapper.model.LearnerProfileEntryRow;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntry;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryDraft;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryKind;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryStatus;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileIdentity;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileOriginType;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileRepository;

/** PostgreSQL/MyBatis 画像存储适配。 */
public class MyBatisLearnerProfileRepository implements LearnerProfileRepository {

  private final LearnerProfileMapper mapper;

  public MyBatisLearnerProfileRepository(LearnerProfileMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<LearnerProfileEntry> findCurrent(LearnerProfileIdentity identity) {
    return Optional.ofNullable(mapper.findCurrent(
        identity.userId(), identity.entryKind().name(), identity.dimension().name(), identity.tagId())).map(this::toEntry);
  }

  @Override
  public void lockUser(long userId) {
    if (userId < 1 || mapper.lockUser(userId) != userId) {
      throw new IllegalStateException("Learner profile user was not found for update");
    }
  }

  @Override
  public Optional<LearnerProfileEntry> findCurrentForUpdate(LearnerProfileIdentity identity) {
    return Optional.ofNullable(mapper.findCurrentForUpdate(
        identity.userId(), identity.entryKind().name(), identity.dimension().name(), identity.tagId())).map(this::toEntry);
  }

  @Override
  public List<LearnerProfileEntry> findHistory(LearnerProfileIdentity identity) {
    return mapper.findHistory(identity.userId(), identity.entryKind().name(), identity.dimension().name(), identity.tagId())
        .stream().map(this::toEntry).toList();
  }

  @Override
  public List<LearnerProfileEntry> findCurrentByDimensions(
      long userId,
      LearnerProfileEntryKind entryKind,
      Collection<org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension> dimensions) {
    if (dimensions == null || dimensions.isEmpty()) {
      return List.of();
    }
    List<String> ordered = dimensions.stream().filter(java.util.Objects::nonNull).map(Enum::name).distinct().sorted().toList();
    if (ordered.isEmpty()) {
      return List.of();
    }
    return mapper.findCurrentByDimensions(userId, entryKind.name(), ordered).stream()
        .map(this::toEntry).sorted(Comparator.comparing(entry -> entry.identity().dimension().name())).toList();
  }

  @Override
  public List<LearnerProfileEntry> findCurrentByTagIds(long userId, Collection<Long> tagIds) {
    if (tagIds == null || tagIds.isEmpty()) {
      return List.of();
    }
    List<Long> ordered = tagIds.stream().filter(tagId -> tagId != null && tagId > 0).distinct().sorted().toList();
    if (ordered.isEmpty()) {
      return List.of();
    }
    return mapper.findCurrentByTagIds(userId, ordered).stream()
        .map(this::toEntry).sorted(Comparator.comparing(LearnerProfileEntry::id)).toList();
  }

  @Override
  public LearnerProfileEntry insert(LearnerProfileEntryDraft draft) {
    LearnerProfileEntryRow row = mapper.insert(draft);
    if (row == null) {
      throw new IllegalStateException("Learner profile insert did not return an entry");
    }
    return toEntry(row);
  }

  @Override
  public void markInactive(long entryId, LearnerProfileEntryStatus status, Instant validTo) {
    if (entryId < 1 || status == LearnerProfileEntryStatus.ACTIVE || validTo == null
        || mapper.markInactive(entryId, status.name(), validTo) != 1) {
      throw new IllegalStateException("Learner profile entry was not marked inactive");
    }
  }

  @Override
  public void deleteIdentity(LearnerProfileIdentity identity) {
    mapper.deleteByIdentity(
        identity.userId(), identity.entryKind().name(), identity.dimension().name(), identity.tagId());
  }

  private LearnerProfileEntry toEntry(LearnerProfileEntryRow row) {
    return new LearnerProfileEntry(
        row.id(),
        new LearnerProfileIdentity(
            row.userId(),
            LearnerProfileEntryKind.valueOf(row.entryKind()),
            org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension.valueOf(row.dimension()),
            row.tagId()),
        row.revisionNo(),
        LearnerProfileEntryStatus.valueOf(row.status()),
        row.contentText(),
        row.supersedesEntryId(),
        LearnerProfileOriginType.valueOf(row.originType()),
        row.modelProvider(), row.modelName(), row.promptVersion(), row.validFrom(), row.validTo(),
        row.createdAt(), row.updatedAt());
  }
}
