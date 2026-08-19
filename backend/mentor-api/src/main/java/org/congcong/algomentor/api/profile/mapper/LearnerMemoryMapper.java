package org.congcong.algomentor.api.profile.mapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.profile.mapper.model.LearnerMemoryClaimRevisionRow;
import org.congcong.algomentor.api.profile.mapper.model.LearnerMemoryMessageEvidenceRow;
import org.congcong.algomentor.api.profile.mapper.model.LearnerMemoryReviewEvidenceRow;
import org.congcong.algomentor.api.profile.mapper.model.LearnerMemoryUpdateRunReviewRow;
import org.congcong.algomentor.api.profile.mapper.model.LearnerMemoryUpdateRunRow;
import org.congcong.algomentor.api.profile.mapper.model.LearnerProfileDocumentClaimRow;
import org.congcong.algomentor.api.profile.mapper.model.LearnerProfileDocumentEvidenceRow;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevisionDraft;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimMessageEvidence;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimReviewEvidence;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRunDraft;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRunReview;

/** 新 Claim/Evidence 存储的独立 MyBatis namespace。 */
public interface LearnerMemoryMapper {

  List<LearnerMemoryClaimRevisionRow> findActiveByUser(@Param("userId") long userId);

  List<LearnerProfileDocumentClaimRow> findActiveDocumentClaims(@Param("userId") long userId);

  boolean existsActiveDocumentClaim(
      @Param("userId") long userId,
      @Param("claimRevisionId") long claimRevisionId);

  List<LearnerProfileDocumentEvidenceRow> findDocumentEvidenceByRevisionIds(
      @Param("userId") long userId,
      @Param("revisionIds") List<Long> revisionIds,
      @Param("claimRevisionId") Long claimRevisionId,
      @Param("locale") String locale);

  List<LearnerProfileDocumentEvidenceRow> findActiveDocumentEvidencePage(
      @Param("userId") long userId,
      @Param("claimRevisionId") long claimRevisionId,
      @Param("revisionIds") List<Long> revisionIds,
      @Param("cursorOccurredAt") Instant cursorOccurredAt,
      @Param("cursorSourceType") String cursorSourceType,
      @Param("cursorSourceId") Long cursorSourceId,
      @Param("limit") int limit,
      @Param("locale") String locale);

  List<LearnerMemoryClaimRevisionRow> findActiveByScopes(
      @Param("userId") long userId,
      @Param("scopes") List<LearnerMemoryClaimScope> scopes);

  List<LearnerMemoryClaimRevisionRow> findActiveByRevisionIds(
      @Param("userId") long userId,
      @Param("revisionIds") List<Long> revisionIds);

  List<LearnerMemoryClaimRevisionRow> findActiveByUserForUpdate(@Param("userId") long userId);

  LearnerMemoryClaimRevisionRow findCurrent(
      @Param("userId") long userId,
      @Param("claimKey") UUID claimKey);

  List<LearnerMemoryClaimRevisionRow> findHistory(
      @Param("userId") long userId,
      @Param("claimKey") UUID claimKey);

  long countActiveByUser(@Param("userId") long userId);

  long countActiveByScope(
      @Param("userId") long userId,
      @Param("scope") LearnerMemoryClaimScope scope);

  long lockUser(@Param("userId") long userId);

  LearnerMemoryClaimRevisionRow insertClaim(@Param("draft") LearnerMemoryClaimRevisionDraft draft);

  int markCurrentSuperseded(@Param("revisionId") long revisionId, @Param("validTo") Instant validTo);

  List<LearnerMemoryReviewEvidenceRow> findReviewEvidenceByRevisionIds(
      @Param("userId") long userId,
      @Param("revisionIds") List<Long> revisionIds);

  List<LearnerMemoryMessageEvidenceRow> findMessageEvidenceByRevisionIds(
      @Param("userId") long userId,
      @Param("revisionIds") List<Long> revisionIds);

  List<Long> findOwnedReviewIds(@Param("userId") long userId, @Param("reviewIds") List<Long> reviewIds);

  List<Long> findOwnedMessageIds(@Param("userId") long userId, @Param("messageIds") List<Long> messageIds);

  int insertReviewEvidence(@Param("evidence") List<LearnerMemoryClaimReviewEvidence> evidence);

  int insertMessageEvidence(@Param("evidence") List<LearnerMemoryClaimMessageEvidence> evidence);

  LearnerMemoryUpdateRunRow findUpdateRunByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

  LearnerMemoryUpdateRunRow findUpdateRunById(@Param("updateRunId") long updateRunId);

  LearnerMemoryUpdateRunRow insertUpdateRun(@Param("draft") LearnerMemoryUpdateRunDraft draft);

  int insertUpdateRunReviews(@Param("reviews") List<LearnerMemoryUpdateRunReview> reviews);

  List<LearnerMemoryUpdateRunReviewRow> findUpdateRunReviews(@Param("updateRunId") long updateRunId);

  int bindAgentRun(@Param("updateRunId") long updateRunId, @Param("agentRunId") long agentRunId);

  int restartFailedUpdateRun(@Param("updateRunId") long updateRunId, @Param("restartedAt") Instant restartedAt);

  int completeUpdateRun(
      @Param("updateRunId") long updateRunId,
      @Param("status") LearnerMemoryRunContract.Status status,
      @Param("operationCount") int operationCount,
      @Param("toolCallCount") int toolCallCount,
      @Param("failureCode") String failureCode,
      @Param("completedAt") Instant completedAt);
}
