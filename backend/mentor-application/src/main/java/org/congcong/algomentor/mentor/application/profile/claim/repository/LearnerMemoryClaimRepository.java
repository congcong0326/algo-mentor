package org.congcong.algomentor.mentor.application.profile.claim.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevisionDraft;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;

/** Claim revision 的事务内读写端口。 */
public interface LearnerMemoryClaimRepository {

  List<LearnerMemoryClaimRevision> findActiveByUser(long userId);

  List<LearnerMemoryClaimRevision> findActiveByScopes(long userId, Collection<LearnerMemoryClaimScope> scopes);

  List<LearnerMemoryClaimRevision> findActiveByRevisionIds(long userId, Collection<Long> revisionIds);

  List<LearnerMemoryClaimRevision> findActiveByUserForUpdate(long userId);

  Optional<LearnerMemoryClaimRevision> findCurrent(long userId, UUID claimKey);

  List<LearnerMemoryClaimRevision> findHistory(long userId, UUID claimKey);

  long countActiveByUser(long userId);

  long countActiveByScope(long userId, LearnerMemoryClaimScope scope);

  void lockUser(long userId);

  LearnerMemoryClaimRevision insert(LearnerMemoryClaimRevisionDraft draft);

  void markCurrentSuperseded(long revisionId, Instant validTo);
}
