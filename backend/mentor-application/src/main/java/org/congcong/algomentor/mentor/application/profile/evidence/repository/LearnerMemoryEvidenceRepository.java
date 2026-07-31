package org.congcong.algomentor.mentor.application.profile.evidence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimMessageEvidence;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimReviewEvidence;

/** Claim evidence 的批量读取、来源归属校验与写入端口。 */
public interface LearnerMemoryEvidenceRepository {

  List<LearnerMemoryClaimReviewEvidence> findReviewEvidenceByRevisionIds(
      long userId, Collection<Long> revisionIds);

  List<LearnerMemoryClaimMessageEvidence> findMessageEvidenceByRevisionIds(
      long userId, Collection<Long> revisionIds);

  Set<Long> findOwnedReviewIds(long userId, Collection<Long> reviewIds);

  Set<Long> findOwnedMessageIds(long userId, Collection<Long> messageIds);

  void insertReviewEvidence(Collection<LearnerMemoryClaimReviewEvidence> evidence);

  void insertMessageEvidence(Collection<LearnerMemoryClaimMessageEvidence> evidence);
}
