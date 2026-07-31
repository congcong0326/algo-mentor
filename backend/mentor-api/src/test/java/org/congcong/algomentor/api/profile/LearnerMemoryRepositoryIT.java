package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.congcong.algomentor.api.profile.mapper.LearnerMemoryMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryClaimRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryEvidenceRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryUpdateRunRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevisionDraft;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimTextHasher;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimMessageEvidence;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimReviewEvidence;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRun;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRunDraft;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRunReview;
import org.junit.jupiter.api.Test;

class LearnerMemoryRepositoryIT extends PostgresIntegrationTestSupport {

  private static final LearnerMemoryClaimScope DECLARED_SCOPE = new LearnerMemoryClaimScope(
      LearnerMemoryClaimContract.Kind.DECLARED_FACT,
      LearnerMemoryClaimContract.Dimension.LEARNER_BACKGROUND,
      null);

  private final LearnerMemoryClaimTextHasher textHasher = new LearnerMemoryClaimTextHasher();

  @Test
  void isolatesUsersReadsBatchesAndPreservesSupersededHistory() throws Exception {
    migrateLatest();
    long firstUserId = insertUser();
    long secondUserId = insertUser();
    MemoryRepositories repositories = repositories();
    LearnerMemoryUpdateRun firstRun = createRun(repositories, firstUserId, "first-run");
    LearnerMemoryUpdateRun secondRun = createRun(repositories, secondUserId, "second-run");
    LearnerMemoryClaimRevision firstClaim = insertClaim(
        repositories, firstRun.id(), firstUserId, DECLARED_SCOPE, "first claim");
    LearnerMemoryClaimRevision secondClaim = insertClaim(
        repositories, secondRun.id(), secondUserId, DECLARED_SCOPE, "second claim");

    assertThat(repositories.claims.findActiveByUser(firstUserId)).containsExactly(firstClaim);
    assertThat(repositories.claims.findActiveByScopes(firstUserId, List.of(DECLARED_SCOPE))).containsExactly(firstClaim);
    assertThat(repositories.claims.findActiveByRevisionIds(firstUserId, List.of(secondClaim.id(), firstClaim.id())))
        .containsExactly(firstClaim);
    assertThat(repositories.claims.findActiveByRevisionIds(firstUserId, List.of())).isEmpty();
    assertThat(repositories.claims.countActiveByUser(firstUserId)).isEqualTo(1L);
    assertThat(repositories.claims.countActiveByScope(firstUserId, DECLARED_SCOPE)).isEqualTo(1L);

    transactionTemplate().executeWithoutResult(status -> repositories.claims.lockUser(firstUserId));
    repositories.claims.markCurrentSuperseded(firstClaim.id(), Instant.now());

    assertThat(repositories.claims.findActiveByUser(firstUserId)).isEmpty();
    assertThat(repositories.claims.findCurrent(firstUserId, firstClaim.claimKey())).isEmpty();
    assertThat(repositories.claims.findHistory(firstUserId, firstClaim.claimKey()))
        .singleElement()
        .extracting(LearnerMemoryClaimRevision::status)
        .isEqualTo(LearnerMemoryClaimContract.RevisionStatus.SUPERSEDED);
  }

  @Test
  void ordersEvidenceBySourceTimeAndKeepsIdempotentRunReviewSequence() throws Exception {
    migrateLatest();
    long userId = insertUser();
    MemoryRepositories repositories = repositories();
    LearnerMemoryUpdateRun run = createRun(repositories, userId, "evidence-run");
    LearnerMemoryClaimRevision claim = insertClaim(repositories, run.id(), userId, DECLARED_SCOPE, "evidence claim");
    long sessionId = insertPracticeSession(userId, "two-sum");
    long laterReviewId = insertReview(userId, sessionId, 1);
    long earlierReviewId = insertReview(userId, sessionId, 2);
    execute("UPDATE practice_code_review SET created_at = NOW() - INTERVAL '2 hours' WHERE id = ?", earlierReviewId);
    long laterMessageId = insertUserMessage(userId);
    long earlierMessageId = insertUserMessage(userId);
    execute("UPDATE agent_message SET created_at = NOW() - INTERVAL '2 hours' WHERE id = ?", earlierMessageId);

    repositories.evidence.insertReviewEvidence(List.of(
        new LearnerMemoryClaimReviewEvidence(
            claim.id(), laterReviewId, LearnerMemoryEvidenceContract.ReviewRole.OBSERVED, 1, Instant.now()),
        new LearnerMemoryClaimReviewEvidence(
            claim.id(), earlierReviewId, LearnerMemoryEvidenceContract.ReviewRole.PERSISTED, 2, Instant.now())));
    repositories.evidence.insertMessageEvidence(List.of(
        new LearnerMemoryClaimMessageEvidence(
            claim.id(), laterMessageId, LearnerMemoryEvidenceContract.MessageRole.DECLARED, 1, Instant.now()),
        new LearnerMemoryClaimMessageEvidence(
            claim.id(), earlierMessageId, LearnerMemoryEvidenceContract.MessageRole.CORRECTED, 2, Instant.now())));

    assertThat(repositories.evidence.findReviewEvidenceByRevisionIds(userId, List.of(claim.id())))
        .extracting(LearnerMemoryClaimReviewEvidence::reviewId)
        .containsExactly(earlierReviewId, laterReviewId);
    assertThat(repositories.evidence.findMessageEvidenceByRevisionIds(userId, List.of(claim.id())))
        .extracting(LearnerMemoryClaimMessageEvidence::messageId)
        .containsExactly(earlierMessageId, laterMessageId);
    assertThat(repositories.evidence.findOwnedReviewIds(userId, List.of(earlierReviewId, laterReviewId)))
        .containsExactlyInAnyOrder(earlierReviewId, laterReviewId);
    assertThat(repositories.evidence.findOwnedMessageIds(userId, List.of(earlierMessageId, laterMessageId)))
        .containsExactlyInAnyOrder(earlierMessageId, laterMessageId);

    long thirdReviewId = insertReview(userId, sessionId, 3);
    long fourthReviewId = insertReview(userId, sessionId, 4);
    long fifthReviewId = insertReview(userId, sessionId, 5);
    repositories.runs.insertTriggerReviews(List.of(
        new LearnerMemoryUpdateRunReview(run.id(), fifthReviewId, 5),
        new LearnerMemoryUpdateRunReview(run.id(), laterReviewId, 2),
        new LearnerMemoryUpdateRunReview(run.id(), thirdReviewId, 3),
        new LearnerMemoryUpdateRunReview(run.id(), earlierReviewId, 1),
        new LearnerMemoryUpdateRunReview(run.id(), fourthReviewId, 4)));

    assertThat(repositories.runs.findByIdempotencyKey("evidence-run")).contains(run);
    assertThat(repositories.runs.findTriggerReviews(run.id()))
        .extracting(LearnerMemoryUpdateRunReview::reviewId)
        .containsExactly(earlierReviewId, laterReviewId, thirdReviewId, fourthReviewId, fifthReviewId);
  }

  private MemoryRepositories repositories() throws Exception {
    LearnerMemoryMapper mapper = sqlSessionTemplate("mapper/profile/LearnerMemoryMapper.xml")
        .getMapper(LearnerMemoryMapper.class);
    return new MemoryRepositories(
        new MyBatisLearnerMemoryClaimRepository(mapper),
        new MyBatisLearnerMemoryEvidenceRepository(mapper),
        new MyBatisLearnerMemoryUpdateRunRepository(mapper));
  }

  private LearnerMemoryUpdateRun createRun(MemoryRepositories repositories, long userId, String idempotencyKey) {
    return repositories.runs.create(new LearnerMemoryUpdateRunDraft(
        userId,
        LearnerMemoryRunContract.Trigger.CODE_REVIEW_BATCH,
        idempotencyKey,
        "v1",
        "v1",
        5,
        Instant.now()));
  }

  private LearnerMemoryClaimRevision insertClaim(
      MemoryRepositories repositories,
      long updateRunId,
      long userId,
      LearnerMemoryClaimScope scope,
      String text) {
    Instant now = Instant.now();
    return repositories.claims.insert(new LearnerMemoryClaimRevisionDraft(
        UUID.randomUUID(),
        userId,
        scope,
        1,
        LearnerMemoryClaimContract.RevisionStatus.ACTIVE,
        text,
        textHasher.hash(text),
        LearnerMemoryClaimContract.Origin.SYSTEM_DERIVED,
        LearnerMemoryEvidenceContract.Pattern.CROSS_PROBLEM_RECURRENCE,
        LearnerMemoryEvidenceContract.Grade.SUPPORTED,
        null,
        updateRunId,
        null,
        now,
        null));
  }

  private long insertReview(long userId, long sessionId, int versionNo) throws SQLException {
    long messageId = insertUserMessage(userId);
    return queryLong(
        """
        INSERT INTO practice_code_review (
          user_id, plan_id, phase_index, problem_slug, practice_session_id, version_no, user_message_id,
          raw_code, normalized_code, language, context_summary, total_score, correctness_score,
          complexity_score, edge_case_score, code_quality_score, problem_fit_score, passed, review_markdown)
        VALUES (?, 1, 1, 'two-sum', ?, ?, ?, 'class Solution {}', 'class Solution {}', 'JAVA',
          'context', 10.0, 4.0, 2.0, 2.0, 1.0, 1.0, TRUE, 'review')
        RETURNING id
        """,
        userId,
        sessionId,
        versionNo,
        messageId);
  }

  private record MemoryRepositories(
      MyBatisLearnerMemoryClaimRepository claims,
      MyBatisLearnerMemoryEvidenceRepository evidence,
      MyBatisLearnerMemoryUpdateRunRepository runs) {
  }
}
