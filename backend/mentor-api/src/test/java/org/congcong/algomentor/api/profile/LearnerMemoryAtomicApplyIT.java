package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.congcong.algomentor.api.profile.mapper.LearnerMemoryMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryClaimRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryEvidenceRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryUpdateRunRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimSnapshotFactory;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimTextHasher;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceReferences;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceValidationContext;
import org.congcong.algomentor.mentor.application.profile.evidence.service.LearnerMemoryEvidenceGradeCalculator;
import org.congcong.algomentor.mentor.application.profile.evidence.service.LearnerMemoryEvidenceValidator;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperation;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperationBatch;
import org.congcong.algomentor.mentor.application.profile.operation.service.LearnerMemoryAtomicApplyService;
import org.congcong.algomentor.mentor.application.profile.operation.service.LearnerMemoryOperationFailure;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRun;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRunDraft;
import org.congcong.algomentor.mentor.application.profile.run.service.LearnerMemoryUpdateRunLifecycleService;
import org.junit.jupiter.api.Test;

class LearnerMemoryAtomicApplyIT extends PostgresIntegrationTestSupport {

  private static final LearnerMemoryClaimScope DECLARED_SCOPE = new LearnerMemoryClaimScope(
      LearnerMemoryClaimContract.Kind.DECLARED_FACT,
      LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS,
      null);

  private final LearnerMemoryClaimSnapshotFactory snapshots = new LearnerMemoryClaimSnapshotFactory();

  @Test
  void writesAddConfirmReviseRetireAsSelfContainedRevisionHistory() throws Exception {
    migrateLatest();
    long userId = insertUser();
    Repositories repositories = repositories();
    LearnerMemoryAtomicApplyService service = service(repositories);
    long firstMessage = insertUserMessage(userId);
    long secondMessage = insertUserMessage(userId);
    long thirdMessage = insertUserMessage(userId);
    long fourthMessage = insertUserMessage(userId);

    LearnerMemoryUpdateRun addRun = run(repositories, userId, "apply-add");
    LearnerMemoryAtomicApplyService.ApplyResult addResult = service.apply(batch(
        userId, addRun, repositories, List.of(add("base claim", firstMessage))), context(firstMessage));
    LearnerMemoryClaimRevision first = addResult.snapshot().activeClaims().get(0);

    LearnerMemoryUpdateRun confirmRun = run(repositories, userId, "apply-confirm");
    LearnerMemoryAtomicApplyService.ApplyResult confirmResult = service.apply(batch(
        userId, confirmRun, repositories, List.of(new LearnerMemoryOperation.Confirm(
            first.id(), declaration(secondMessage), null))), context(firstMessage, secondMessage));
    LearnerMemoryClaimRevision second = confirmResult.snapshot().activeClaims().get(0);

    LearnerMemoryUpdateRun reviseRun = run(repositories, userId, "apply-revise");
    LearnerMemoryAtomicApplyService.ApplyResult reviseResult = service.apply(batch(
        userId, reviseRun, repositories, List.of(new LearnerMemoryOperation.Revise(
            second.id(), "revised claim", declarations(firstMessage, secondMessage, thirdMessage), null))),
        context(firstMessage, secondMessage, thirdMessage));
    LearnerMemoryClaimRevision third = reviseResult.snapshot().activeClaims().get(0);

    LearnerMemoryUpdateRun retireRun = run(repositories, userId, "apply-retire");
    LearnerMemoryAtomicApplyService.ApplyResult retireResult = service.apply(batch(
        userId, retireRun, repositories, List.of(new LearnerMemoryOperation.Retire(
            third.id(), declaration(fourthMessage), null))), context(fourthMessage));

    assertThat(addResult.status()).isEqualTo(LearnerMemoryAtomicApplyService.ApplyStatus.APPLIED);
    assertThat(retireResult.snapshot().activeClaims()).isEmpty();
    assertThat(repositories.claims.findHistory(userId, first.claimKey()))
        .extracting(LearnerMemoryClaimRevision::revisionNo)
        .containsExactly(4, 3, 2, 1);
    assertThat(repositories.claims.findHistory(userId, first.claimKey()))
        .first()
        .extracting(LearnerMemoryClaimRevision::status)
        .isEqualTo(LearnerMemoryClaimContract.RevisionStatus.RETIRED);
    assertThat(repositories.evidence.findMessageEvidenceByRevisionIds(userId, List.of(second.id()))).hasSize(2);
    assertThat(repositories.evidence.findMessageEvidenceByRevisionIds(userId, List.of(third.id()))).hasSize(3);
    assertThat(repositories.runs.findById(retireRun.id())).get()
        .extracting(LearnerMemoryUpdateRun::status)
        .isEqualTo(LearnerMemoryRunContract.Status.SUCCEEDED);
  }

  @Test
  void rollsBackEntireInvalidBatchAndRecordsFailureInIndependentTransaction() throws Exception {
    migrateLatest();
    long userId = insertUser();
    Repositories repositories = repositories();
    LearnerMemoryAtomicApplyService service = service(repositories);
    long existingMessage = insertUserMessage(userId);
    LearnerMemoryUpdateRun existingRun = run(repositories, userId, "existing-claim");
    service.apply(batch(userId, existingRun, repositories, List.of(add("existing claim", existingMessage))),
        context(existingMessage));
    long message = insertUserMessage(userId);
    LearnerMemoryUpdateRun invalidRun = run(repositories, userId, "invalid-batch");

    assertThatThrownBy(() -> service.apply(batch(userId, invalidRun, repositories, List.of(
        add("duplicate batch claim", message), add("duplicate batch claim", message))), context(message)))
        .isInstanceOf(LearnerMemoryOperationFailure.class)
        .extracting(error -> ((LearnerMemoryOperationFailure) error).code())
        .isEqualTo(LearnerMemoryOperationFailure.Code.DUPLICATE_ACTIVE_TEXT);

    assertThat(repositories.claims.findActiveByUser(userId)).hasSize(1);
    assertThat(repositories.runs.findById(invalidRun.id())).get()
        .extracting(LearnerMemoryUpdateRun::status)
        .isEqualTo(LearnerMemoryRunContract.Status.FAILED);
  }

  @Test
  void serializesConcurrentAddsAndReturnsStaleForTheSecondSnapshot() throws Exception {
    migrateLatest();
    long userId = insertUser();
    Repositories repositories = repositories();
    LearnerMemoryAtomicApplyService service = service(repositories);
    long firstMessage = insertUserMessage(userId);
    long secondMessage = insertUserMessage(userId);
    LearnerMemoryUpdateRun firstRun = run(repositories, userId, "concurrent-first");
    LearnerMemoryUpdateRun secondRun = run(repositories, userId, "concurrent-second");
    LearnerMemoryOperationBatch first = batch(
        userId, firstRun, repositories, List.of(add("first concurrent claim", firstMessage)));
    LearnerMemoryOperationBatch second = batch(
        userId, secondRun, repositories, List.of(add("second concurrent claim", secondMessage)));
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<LearnerMemoryAtomicApplyService.ApplyResult> firstResult = executor.submit(applyAfterBarrier(
          service, first, context(firstMessage), ready, start));
      Future<LearnerMemoryAtomicApplyService.ApplyResult> secondResult = executor.submit(applyAfterBarrier(
          service, second, context(secondMessage), ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      assertThat(List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS)))
          .extracting(LearnerMemoryAtomicApplyService.ApplyResult::status)
          .containsExactlyInAnyOrder(
              LearnerMemoryAtomicApplyService.ApplyStatus.APPLIED,
              LearnerMemoryAtomicApplyService.ApplyStatus.STALE);
      assertThat(repositories.claims.findActiveByUser(userId)).hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }

  private LearnerMemoryAtomicApplyService service(Repositories repositories) {
    return new LearnerMemoryAtomicApplyService(
        repositories.claims,
        repositories.evidence,
        repositories.runs,
        new LearnerMemoryClaimTextHasher(),
        snapshots,
        new LearnerMemoryEvidenceValidator(),
        new LearnerMemoryEvidenceGradeCalculator(),
        new LearnerMemoryUpdateRunLifecycleService(repositories.runs, transactionTemplate()),
        transactionTemplate());
  }

  private Repositories repositories() throws Exception {
    LearnerMemoryMapper mapper = sqlSessionTemplate("mapper/profile/LearnerMemoryMapper.xml")
        .getMapper(LearnerMemoryMapper.class);
    return new Repositories(
        new MyBatisLearnerMemoryClaimRepository(mapper),
        new MyBatisLearnerMemoryEvidenceRepository(mapper),
        new MyBatisLearnerMemoryUpdateRunRepository(mapper));
  }

  private LearnerMemoryUpdateRun run(Repositories repositories, long userId, String key) {
    return repositories.runs.create(new LearnerMemoryUpdateRunDraft(
        userId, LearnerMemoryRunContract.Trigger.DECLARED_FACT, key, "v1", "v1", 1, Instant.now()));
  }

  private LearnerMemoryOperationBatch batch(
      long userId,
      LearnerMemoryUpdateRun run,
      Repositories repositories,
      List<LearnerMemoryOperation> operations) {
    return new LearnerMemoryOperationBatch(
        userId,
        run.id(),
        snapshots.create(repositories.claims.findActiveByUser(userId)).token(),
        0,
        operations);
  }

  private LearnerMemoryOperation.Add add(String text, long messageId) {
    return new LearnerMemoryOperation.Add(DECLARED_SCOPE, text, declaration(messageId), null);
  }

  private LearnerMemoryEvidenceReferences declaration(long messageId) {
    return declarations(messageId);
  }

  private LearnerMemoryEvidenceReferences declarations(long... messageIds) {
    return new LearnerMemoryEvidenceReferences(
        LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION,
        List.of(),
        java.util.Arrays.stream(messageIds)
            .mapToObj(id -> new LearnerMemoryEvidenceReferences.MessageReference(
                id, LearnerMemoryEvidenceContract.MessageRole.DECLARED))
            .toList());
  }

  private LearnerMemoryEvidenceValidationContext context(long... messageIds) {
    Instant base = Instant.parse("2026-07-30T00:00:00Z");
    return new LearnerMemoryEvidenceValidationContext(List.of(), java.util.Arrays.stream(messageIds)
        .mapToObj(id -> new LearnerMemoryEvidenceValidationContext.MessageSource(id, base.plusSeconds(id)))
        .toList());
  }

  private Callable<LearnerMemoryAtomicApplyService.ApplyResult> applyAfterBarrier(
      LearnerMemoryAtomicApplyService service,
      LearnerMemoryOperationBatch batch,
      LearnerMemoryEvidenceValidationContext context,
      CountDownLatch ready,
      CountDownLatch start) {
    return () -> {
      ready.countDown();
      if (!start.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("concurrent learner memory apply did not start");
      }
      return service.apply(batch, context);
    };
  }

  private record Repositories(
      MyBatisLearnerMemoryClaimRepository claims,
      MyBatisLearnerMemoryEvidenceRepository evidence,
      MyBatisLearnerMemoryUpdateRunRepository runs) {
  }
}
