package org.congcong.algomentor.api.profile.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.api.profile.mapper.LearnerMemoryMapper;
import org.congcong.algomentor.api.profile.mapper.model.LearnerMemoryClaimRevisionRow;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevisionDraft;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.repository.LearnerMemoryClaimRepository;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;

/** PostgreSQL/MyBatis claim revision 存储适配器。 */
public class MyBatisLearnerMemoryClaimRepository implements LearnerMemoryClaimRepository {

  private static final Comparator<LearnerMemoryClaimScope> SCOPE_ORDER = Comparator
      .comparing((LearnerMemoryClaimScope scope) -> scope.kind().name())
      .thenComparing(scope -> scope.dimension().name())
      .thenComparing(LearnerMemoryClaimScope::tagId, Comparator.nullsFirst(Long::compareTo));

  private final LearnerMemoryMapper mapper;

  public MyBatisLearnerMemoryClaimRepository(LearnerMemoryMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
  }

  @Override
  public List<LearnerMemoryClaimRevision> findActiveByUser(long userId) {
    requirePositive(userId, "user id");
    return mapper.findActiveByUser(userId).stream().map(this::toClaim).toList();
  }

  @Override
  public List<LearnerMemoryClaimRevision> findActiveByScopes(
      long userId, Collection<LearnerMemoryClaimScope> scopes) {
    requirePositive(userId, "user id");
    List<LearnerMemoryClaimScope> values = orderedScopes(scopes);
    return values.isEmpty() ? List.of() : mapper.findActiveByScopes(userId, values).stream()
        .map(this::toClaim)
        .toList();
  }

  @Override
  public List<LearnerMemoryClaimRevision> findActiveByRevisionIds(
      long userId, Collection<Long> revisionIds) {
    requirePositive(userId, "user id");
    List<Long> values = positiveIds(revisionIds);
    return values.isEmpty() ? List.of() : mapper.findActiveByRevisionIds(userId, values).stream()
        .map(this::toClaim)
        .toList();
  }

  @Override
  public List<LearnerMemoryClaimRevision> findActiveByUserForUpdate(long userId) {
    requirePositive(userId, "user id");
    return mapper.findActiveByUserForUpdate(userId).stream().map(this::toClaim).toList();
  }

  @Override
  public Optional<LearnerMemoryClaimRevision> findCurrent(long userId, UUID claimKey) {
    requirePositive(userId, "user id");
    return Optional.ofNullable(mapper.findCurrent(userId, Objects.requireNonNull(claimKey, "claimKey")))
        .map(this::toClaim);
  }

  @Override
  public List<LearnerMemoryClaimRevision> findHistory(long userId, UUID claimKey) {
    requirePositive(userId, "user id");
    return mapper.findHistory(userId, Objects.requireNonNull(claimKey, "claimKey")).stream()
        .map(this::toClaim)
        .toList();
  }

  @Override
  public long countActiveByUser(long userId) {
    requirePositive(userId, "user id");
    return mapper.countActiveByUser(userId);
  }

  @Override
  public long countActiveByScope(long userId, LearnerMemoryClaimScope scope) {
    requirePositive(userId, "user id");
    return mapper.countActiveByScope(userId, Objects.requireNonNull(scope, "scope"));
  }

  @Override
  public void lockUser(long userId) {
    requirePositive(userId, "user id");
    if (mapper.lockUser(userId) != userId) {
      throw new IllegalStateException("Learner memory user was not found for update");
    }
  }

  @Override
  public LearnerMemoryClaimRevision insert(LearnerMemoryClaimRevisionDraft draft) {
    LearnerMemoryClaimRevisionRow row = mapper.insertClaim(Objects.requireNonNull(draft, "draft"));
    if (row == null) {
      throw new IllegalStateException("Learner memory claim insert did not return a revision");
    }
    return toClaim(row);
  }

  @Override
  public void markCurrentSuperseded(long revisionId, Instant validTo) {
    requirePositive(revisionId, "revision id");
    if (validTo == null || mapper.markCurrentSuperseded(revisionId, validTo) != 1) {
      throw new IllegalStateException("Learner memory active revision was not superseded");
    }
  }

  private LearnerMemoryClaimRevision toClaim(LearnerMemoryClaimRevisionRow row) {
    return new LearnerMemoryClaimRevision(
        row.id(),
        UUID.fromString(row.claimKey()),
        row.userId(),
        new LearnerMemoryClaimScope(
            LearnerMemoryClaimContract.Kind.valueOf(row.entryKind()),
            LearnerMemoryClaimContract.Dimension.valueOf(row.dimension()),
            row.tagId()),
        row.revisionNo(),
        LearnerMemoryClaimContract.RevisionStatus.valueOf(row.status()),
        row.claimText(),
        row.claimTextHash(),
        LearnerMemoryClaimContract.Origin.valueOf(row.originType()),
        LearnerMemoryEvidenceContract.Pattern.valueOf(row.evidencePattern()),
        LearnerMemoryEvidenceContract.Grade.valueOf(row.evidenceGrade()),
        row.decisionReason(),
        row.updateRunId(),
        row.supersedesRevisionId(),
        row.validFrom(),
        row.validTo(),
        row.createdAt(),
        row.updatedAt());
  }

  private static List<LearnerMemoryClaimScope> orderedScopes(Collection<LearnerMemoryClaimScope> scopes) {
    return scopes == null ? List.of() : scopes.stream()
        .filter(Objects::nonNull)
        .distinct()
        .sorted(SCOPE_ORDER)
        .toList();
  }

  private static List<Long> positiveIds(Collection<Long> values) {
    return values == null ? List.of() : values.stream()
        .filter(value -> value != null && value > 0)
        .distinct()
        .sorted()
        .toList();
  }

  private static void requirePositive(long value, String field) {
    if (value <= 0) {
      throw new IllegalArgumentException(field + " must be positive");
    }
  }
}
