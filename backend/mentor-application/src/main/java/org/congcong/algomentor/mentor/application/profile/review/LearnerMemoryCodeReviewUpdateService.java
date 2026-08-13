package org.congcong.algomentor.mentor.application.profile.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimQueryService;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimSnapshot;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimReviewEvidence;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceValidationContext;
import org.congcong.algomentor.mentor.application.profile.evidence.repository.LearnerMemoryEvidenceRepository;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperation;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperationBatch;
import org.congcong.algomentor.mentor.application.profile.operation.service.LearnerMemoryAtomicApplyService;
import org.congcong.algomentor.mentor.application.profile.operation.service.LearnerMemoryOperationFailure;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistoryRepository;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRun;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRunDraft;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRunReview;
import org.congcong.algomentor.mentor.application.profile.run.repository.LearnerMemoryUpdateRunRepository;
import org.congcong.algomentor.mentor.application.profile.run.service.LearnerMemoryUpdateRunLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Code Review 批次的幂等 Claim 更新编排；模型与工具调用始终在数据库事务外。 */
public class LearnerMemoryCodeReviewUpdateService {

  private static final Logger log = LoggerFactory.getLogger(LearnerMemoryCodeReviewUpdateService.class);

  private final LearnerMemoryCodeReviewFactRepository factRepository;
  private final CodeReviewHistoryRepository historyRepository;
  private final LearnerMemoryClaimQueryService claimQueryService;
  private final LearnerMemoryEvidenceRepository evidenceRepository;
  private final LearnerMemoryUpdateRunRepository updateRunRepository;
  private final LearnerMemoryAtomicApplyService atomicApplyService;
  private final LearnerMemoryUpdateRunLifecycleService runLifecycleService;
  private final AgentRuntime agentRuntime;
  private final LearnerMemoryCodeReviewStructuredOutputMapper outputMapper;
  private final int maxStaleRetries;
  private final LearnerMemoryMetrics metrics;

  public LearnerMemoryCodeReviewUpdateService(
      LearnerMemoryCodeReviewFactRepository factRepository,
      CodeReviewHistoryRepository historyRepository,
      LearnerMemoryClaimQueryService claimQueryService,
      LearnerMemoryEvidenceRepository evidenceRepository,
      LearnerMemoryUpdateRunRepository updateRunRepository,
      LearnerMemoryAtomicApplyService atomicApplyService,
      LearnerMemoryUpdateRunLifecycleService runLifecycleService,
      AgentRuntime agentRuntime,
      LearnerMemoryCodeReviewStructuredOutputMapper outputMapper,
      int maxStaleRetries
  ) {
    this(
        factRepository, historyRepository, claimQueryService, evidenceRepository, updateRunRepository,
        atomicApplyService, runLifecycleService, agentRuntime, outputMapper, maxStaleRetries,
        LearnerMemoryMetrics.NOOP);
  }

  public LearnerMemoryCodeReviewUpdateService(
      LearnerMemoryCodeReviewFactRepository factRepository,
      CodeReviewHistoryRepository historyRepository,
      LearnerMemoryClaimQueryService claimQueryService,
      LearnerMemoryEvidenceRepository evidenceRepository,
      LearnerMemoryUpdateRunRepository updateRunRepository,
      LearnerMemoryAtomicApplyService atomicApplyService,
      LearnerMemoryUpdateRunLifecycleService runLifecycleService,
      AgentRuntime agentRuntime,
      LearnerMemoryCodeReviewStructuredOutputMapper outputMapper,
      int maxStaleRetries,
      LearnerMemoryMetrics metrics
  ) {
    if (maxStaleRetries < 0 || maxStaleRetries > LearnerMemoryCodeReviewConsumerConstants.MAX_STALE_RETRIES) {
      throw new IllegalArgumentException("Code review memory stale retries are invalid");
    }
    this.factRepository = factRepository;
    this.historyRepository = historyRepository;
    this.claimQueryService = claimQueryService;
    this.evidenceRepository = evidenceRepository;
    this.updateRunRepository = updateRunRepository;
    this.atomicApplyService = atomicApplyService;
    this.runLifecycleService = runLifecycleService;
    this.agentRuntime = agentRuntime;
    this.outputMapper = outputMapper;
    this.maxStaleRetries = maxStaleRetries;
    this.metrics = Objects.requireNonNullElse(metrics, LearnerMemoryMetrics.NOOP);
  }

  public LearnerMemoryCodeReviewUpdateResult update(long userId, List<LearnerMemoryCodeReviewFact> batchFacts) {
    return update(userId, batchFacts, 1);
  }

  /** 队列重投时为 Agent invocation 生成新的幂等键，但保留同一批业务更新 run。 */
  public LearnerMemoryCodeReviewUpdateResult update(
      long userId, List<LearnerMemoryCodeReviewFact> batchFacts, int deliveryAttempt) {
    if (deliveryAttempt < 1) {
      throw new IllegalArgumentException("Code review memory delivery attempt is invalid");
    }
    LearnerMemoryUpdateRun updateRun = null;
    int toolCallCount = 0;
    int windowProblemCount = 0;
    try {
      List<Long> batchReviewIds = validatedBatchReviewIds(userId, batchFacts);
      List<CodeReviewVerification> batchReviews = verifiedReviews(userId, batchReviewIds);
      String idempotencyKey = backgroundIdempotencyKey(userId, batchFacts);
      updateRun = findOrCreateRun(userId, batchReviewIds, idempotencyKey);
      if (updateRun.status() == LearnerMemoryRunContract.Status.FAILED) {
        updateRunRepository.restartFailed(updateRun.id(), Instant.now());
        updateRun = updateRunRepository.findById(updateRun.id())
            .orElseThrow(() -> new IllegalStateException("Restarted code review memory run is missing"));
      }
      if (updateRun.status().isTerminal()) {
        return terminalResult(updateRun, 0);
      }

      List<LearnerMemoryCodeReviewFact> window = window(userId, batchFacts);
      windowProblemCount = window.size();
      List<CodeReviewVerification> scopeReviews = verifiedReviews(userId, unionReviewIds(batchReviews, window));
      LearnerMemoryClaimSnapshot snapshot = claimQueryService.snapshot(userId);
      Long retryOfRunId = null;
      for (int attempt = 0; attempt <= maxStaleRetries; attempt++) {
        LearnerMemoryCodeReviewUpdateAgentInput input = input(
            userId,
            window,
            scopeReviews,
            snapshot,
            attemptIdempotencyKey(deliveryAttemptIdempotencyKey(idempotencyKey, deliveryAttempt), attempt),
            retryOfRunId);
        DecisionRound round = decide(userId, input);
        toolCallCount = round.toolCallCount();
        updateRunRepository.bindAgentRun(updateRun.id(), round.agentRunId());
        List<LearnerMemoryOperation> operations = outputMapper.map(round.structuredOutput(), input);
        LearnerMemoryOperationBatch batch = new LearnerMemoryOperationBatch(
            userId,
            updateRun.id(),
            input.snapshotToken(),
            round.toolCallCount(),
            operations);
        LearnerMemoryAtomicApplyService.ApplyResult result = atomicApplyService.apply(
            batch, evidenceContext(input.evidenceReviews()));
        if (result.status() == LearnerMemoryAtomicApplyService.ApplyStatus.STALE) {
          metrics.recordInvalidOutput("STALE");
          if (attempt == maxStaleRetries) {
            runLifecycleService.markFailed(
                userId, updateRun.id(), toolCallCount, LearnerMemoryOperationFailure.Code.STALE_SNAPSHOT);
            return failed(updateRun.id(), windowProblemCount);
          }
          retryOfRunId = round.agentRunId();
          snapshot = claimQueryService.snapshot(userId);
          continue;
        }
        return new LearnerMemoryCodeReviewUpdateResult(
            result.status() == LearnerMemoryAtomicApplyService.ApplyStatus.APPLIED
                ? LearnerMemoryCodeReviewUpdateResult.Status.UPDATED
                : LearnerMemoryCodeReviewUpdateResult.Status.NO_CHANGE,
            updateRun.id(),
            windowProblemCount,
            result.operationCount());
      }
    } catch (LearnerMemoryOperationFailure failure) {
      metrics.recordInvalidOutput("VALIDATION");
      log.info("Code review memory update rejected. code={}", failure.code());
      if (updateRun != null) {
        runLifecycleService.markFailed(userId, updateRun.id(), toolCallCount, failure.code());
      }
    } catch (RuntimeException exception) {
      metrics.recordInvalidOutput("TOOL_FAILURE");
      log.warn("Code review memory update failed. exceptionType={}", exception.getClass().getSimpleName());
      if (updateRun != null) {
        runLifecycleService.markFailed(
            userId, updateRun.id(), toolCallCount, LearnerMemoryOperationFailure.Code.AGENT_FAILURE);
      }
    }
    return failed(updateRun == null ? null : updateRun.id(), windowProblemCount);
  }

  static String backgroundIdempotencyKey(long userId, List<LearnerMemoryCodeReviewFact> batchFacts) {
    List<Long> reviewIds = validatedBatchReviewIds(userId, batchFacts);
    return LearnerMemoryCodeReviewConsumerConstants.BACKGROUND_IDEMPOTENCY_KEY_PREFIX
        + sha256(userId + "\u001d" + reviewIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
  }

  private static String deliveryAttemptIdempotencyKey(String idempotencyKey, int deliveryAttempt) {
    return deliveryAttempt == 1
        ? idempotencyKey
        : idempotencyKey + LearnerMemoryCodeReviewConsumerConstants.BACKGROUND_RETRY_IDEMPOTENCY_KEY_SEPARATOR
            + "delivery-" + deliveryAttempt;
  }

  private LearnerMemoryUpdateRun findOrCreateRun(long userId, List<Long> reviewIds, String idempotencyKey) {
    return updateRunRepository.findByIdempotencyKey(idempotencyKey).map(existing -> {
      if (existing.userId() != userId || existing.trigger() != LearnerMemoryRunContract.Trigger.CODE_REVIEW_BATCH) {
        throw new IllegalStateException("Code review memory update run does not match trusted batch");
      }
      List<Long> storedIds = updateRunRepository.findTriggerReviews(existing.id()).stream()
          .sorted(Comparator.comparingInt(LearnerMemoryUpdateRunReview::sequenceNo))
          .map(LearnerMemoryUpdateRunReview::reviewId)
          .toList();
      if (!storedIds.equals(reviewIds)) {
        throw new IllegalStateException("Code review memory update run trigger reviews do not match");
      }
      return existing;
    }).orElseGet(() -> {
      LearnerMemoryUpdateRun created = updateRunRepository.create(new LearnerMemoryUpdateRunDraft(
          userId,
          LearnerMemoryRunContract.Trigger.CODE_REVIEW_BATCH,
          idempotencyKey,
          LearnerMemoryCodeReviewConsumerConstants.PROMPT_VERSION,
          LearnerMemoryCodeReviewConsumerConstants.SCHEMA_VERSION,
          reviewIds.size(),
          Instant.now()));
      updateRunRepository.insertTriggerReviews(reviewIds.stream()
          .map(reviewId -> new LearnerMemoryUpdateRunReview(created.id(), reviewId, reviewIds.indexOf(reviewId) + 1))
          .toList());
      return created;
    });
  }

  private List<LearnerMemoryCodeReviewFact> window(long userId, List<LearnerMemoryCodeReviewFact> batchFacts) {
    List<String> batchSlugs = batchFacts.stream().map(LearnerMemoryCodeReviewFact::problemSlug).distinct().sorted().toList();
    Map<String, LearnerMemoryCodeReviewFact> bySlug = new LinkedHashMap<>();
    addLatest(bySlug, factRepository.findLatestForProblemSlugs(userId, batchSlugs));
    if (bySlug.size() < LearnerMemoryCodeReviewConsumerConstants.MAX_DISTINCT_PROBLEMS) {
      addLatest(bySlug, factRepository.findRecentDistinctProblems(
          userId,
          List.copyOf(bySlug.keySet()),
          LearnerMemoryCodeReviewConsumerConstants.MAX_DISTINCT_PROBLEMS - bySlug.size()));
    }
    return bySlug.values().stream()
        .sorted(Comparator.comparing(LearnerMemoryCodeReviewFact::createdAt).reversed()
            .thenComparing(LearnerMemoryCodeReviewFact::problemSlug))
        .limit(LearnerMemoryCodeReviewConsumerConstants.MAX_DISTINCT_PROBLEMS)
        .toList();
  }

  private void addLatest(Map<String, LearnerMemoryCodeReviewFact> bySlug, List<LearnerMemoryCodeReviewFact> values) {
    for (LearnerMemoryCodeReviewFact fact : values == null ? List.<LearnerMemoryCodeReviewFact>of() : values) {
      if (fact == null) {
        throw new IllegalStateException("Code review memory window contains null fact");
      }
      bySlug.merge(fact.problemSlug(), fact, (first, second) -> latest(first, second));
    }
  }

  private LearnerMemoryCodeReviewFact latest(LearnerMemoryCodeReviewFact first, LearnerMemoryCodeReviewFact second) {
    return Comparator.comparing(LearnerMemoryCodeReviewFact::createdAt)
        .thenComparingInt(LearnerMemoryCodeReviewFact::versionNo)
        .thenComparingLong(LearnerMemoryCodeReviewFact::reviewId)
        .compare(first, second) >= 0 ? first : second;
  }

  private LearnerMemoryCodeReviewUpdateAgentInput input(
      long userId,
      List<LearnerMemoryCodeReviewFact> window,
      List<CodeReviewVerification> scopeReviews,
      LearnerMemoryClaimSnapshot snapshot,
      String idempotencyKey,
      Long retryOfRunId
  ) {
    List<LearnerMemoryClaimScope> scopes = allowedScopes(window);
    Set<LearnerMemoryClaimScope> scopeSet = Set.copyOf(scopes);
    Map<Long, List<LearnerMemoryClaimReviewEvidence>> evidenceByRevision = evidenceRepository
        .findReviewEvidenceByRevisionIds(userId, snapshot.activeClaims().stream().map(LearnerMemoryClaimRevision::id).toList())
        .stream().collect(Collectors.groupingBy(LearnerMemoryClaimReviewEvidence::claimRevisionId));
    Set<Long> existingEvidenceIds = evidenceByRevision.values().stream().flatMap(List::stream)
        .map(LearnerMemoryClaimReviewEvidence::reviewId).collect(Collectors.toCollection(LinkedHashSet::new));
    List<CodeReviewVerification> evidenceReviews = mergeVerifications(
        scopeReviews,
        existingEvidenceIds.isEmpty() ? List.of() : verifiedReviews(userId, List.copyOf(existingEvidenceIds)));
    Map<LearnerMemoryClaimScope, Integer> activeByScope = new HashMap<>();
    for (LearnerMemoryClaimRevision claim : snapshot.activeClaims()) {
      activeByScope.merge(claim.scope(), 1, Integer::sum);
    }
    List<LearnerMemoryCodeReviewUpdateAgentInput.ActiveClaim> activeClaims = snapshot.activeClaims().stream()
        .filter(claim -> scopeSet.contains(claim.scope()))
        .map(claim -> new LearnerMemoryCodeReviewUpdateAgentInput.ActiveClaim(
            claim.id(),
            claim.scope(),
            claim.claimText(),
            evidenceByRevision.getOrDefault(claim.id(), List.of()).stream()
                .sorted(Comparator.comparingInt(LearnerMemoryClaimReviewEvidence::sequenceNo)
                    .thenComparingLong(LearnerMemoryClaimReviewEvidence::reviewId))
                .map(evidence -> new LearnerMemoryCodeReviewUpdateAgentInput.ReviewEvidence(
                    evidence.reviewId(), evidence.role()))
                .toList()))
        .toList();
    List<LearnerMemoryCodeReviewUpdateAgentInput.ScopeCapacity> capacities = scopes.stream()
        .map(scope -> new LearnerMemoryCodeReviewUpdateAgentInput.ScopeCapacity(
            scope,
            activeByScope.getOrDefault(scope, 0),
            scopeLimit(scope)))
        .toList();
    return new LearnerMemoryCodeReviewUpdateAgentInput(
        userId,
        window,
        scopeReviews,
        evidenceReviews,
        activeClaims,
        capacities,
        snapshot.token(),
        snapshot.activeClaims().size(),
        capacityState(snapshot.activeClaims().size()),
        idempotencyKey,
        retryOfRunId);
  }

  private List<LearnerMemoryClaimScope> allowedScopes(List<LearnerMemoryCodeReviewFact> window) {
    List<LearnerMemoryClaimScope> scopes = new ArrayList<>();
    for (org.congcong.algomentor.mentor.application.profile.LearnerMemoryClaimDimension dimension
        : LearnerMemoryCodeReviewConsumerConstants.GENERAL_DIMENSIONS) {
      scopes.add(new LearnerMemoryClaimScope(
          LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
          LearnerMemoryClaimContract.Dimension.valueOf(dimension.name()),
          null));
    }
    window.stream().flatMap(fact -> fact.affectedTagIds().stream()).distinct().sorted().forEach(tagId -> scopes.add(
        new LearnerMemoryClaimScope(
            LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT,
            LearnerMemoryClaimContract.Dimension.TAG_MASTERY,
            tagId)));
    return List.copyOf(scopes);
  }

  private int scopeLimit(LearnerMemoryClaimScope scope) {
    return switch (scope.kind()) {
      case GENERAL_OBSERVATION -> LearnerMemoryClaimContract.GENERAL_SCOPE_ACTIVE_LIMIT;
      case TAG_ASSESSMENT -> LearnerMemoryClaimContract.TAG_SCOPE_ACTIVE_LIMIT;
      case DECLARED_FACT -> throw new IllegalArgumentException("Code review memory cannot update declared scope");
    };
  }

  private LearnerMemoryClaimContract.CapacityState capacityState(int activeClaimCount) {
    return activeClaimCount >= LearnerMemoryClaimContract.USER_ACTIVE_HARD_LIMIT
        ? LearnerMemoryClaimContract.CapacityState.HARD_LIMIT
        : activeClaimCount >= LearnerMemoryClaimContract.USER_ACTIVE_SOFT_LIMIT
            ? LearnerMemoryClaimContract.CapacityState.SOFT_LIMIT
            : LearnerMemoryClaimContract.CapacityState.NORMAL;
  }

  private DecisionRound decide(long userId, LearnerMemoryCodeReviewUpdateAgentInput input) {
    AgentRunResult result = agentRuntime.execute(new AgentInvocation<>(
        LearnerMemoryCodeReviewUpdateAgentDefinition.KEY,
        input,
        new AgentInvocationContext(
            userId,
            AgentInvocationMode.BACKGROUND,
            input.idempotencyKey(),
            null,
            null,
            input.windowFacts().size(),
            false)));
    return new DecisionRound(
        result.output() == null ? null : result.output().structured(),
        requiredPositiveLong(result.metadata(), AgentRuntimeMetadataKeys.RUN_DB_ID),
        input.toolCallCount());
  }

  private LearnerMemoryEvidenceValidationContext evidenceContext(List<CodeReviewVerification> reviews) {
    return new LearnerMemoryEvidenceValidationContext(reviews.stream().map(review ->
        new LearnerMemoryEvidenceValidationContext.ReviewSource(
            review.reviewId(),
            review.problemSlug(),
            review.versionNo(),
            Set.copyOf(review.affectedTagIds()),
            review.createdAt())).toList(), List.of());
  }

  private List<Long> unionReviewIds(List<CodeReviewVerification> batchReviews, List<LearnerMemoryCodeReviewFact> window) {
    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    batchReviews.forEach(review -> ids.add(review.reviewId()));
    window.forEach(fact -> ids.add(fact.reviewId()));
    return List.copyOf(ids);
  }

  private List<CodeReviewVerification> verifiedReviews(long userId, List<Long> reviewIds) {
    List<CodeReviewVerification> found = historyRepository.verifyReviews(userId, reviewIds);
    Map<Long, CodeReviewVerification> byId = found.stream().collect(Collectors.toMap(
        CodeReviewVerification::reviewId,
        review -> review,
        (first, second) -> {
          throw new IllegalStateException("Code review memory verification is duplicated");
        },
        LinkedHashMap::new));
    if (byId.size() != reviewIds.size() || !byId.keySet().containsAll(reviewIds)) {
      throw new IllegalStateException("Code review memory review ownership is invalid");
    }
    return reviewIds.stream().map(byId::get).toList();
  }

  private List<CodeReviewVerification> mergeVerifications(
      List<CodeReviewVerification> first,
      List<CodeReviewVerification> second
  ) {
    Map<Long, CodeReviewVerification> byId = new LinkedHashMap<>();
    for (CodeReviewVerification review : first) {
      byId.put(review.reviewId(), review);
    }
    for (CodeReviewVerification review : second) {
      CodeReviewVerification existing = byId.putIfAbsent(review.reviewId(), review);
      if (existing != null && !existing.equals(review)) {
        throw new IllegalStateException("Code review memory verification facts conflict");
      }
    }
    return List.copyOf(byId.values());
  }

  private LearnerMemoryCodeReviewUpdateResult terminalResult(LearnerMemoryUpdateRun run, int windowProblemCount) {
    return switch (run.status()) {
      case SUCCEEDED -> new LearnerMemoryCodeReviewUpdateResult(
          LearnerMemoryCodeReviewUpdateResult.Status.UPDATED, run.id(), windowProblemCount, run.operationCount());
      case NO_CHANGE -> new LearnerMemoryCodeReviewUpdateResult(
          LearnerMemoryCodeReviewUpdateResult.Status.NO_CHANGE, run.id(), windowProblemCount, 0);
      case FAILED -> failed(run.id(), windowProblemCount);
      case RUNNING -> throw new IllegalStateException("Code review memory update run is not terminal");
    };
  }

  private LearnerMemoryCodeReviewUpdateResult failed(Long updateRunId, int windowProblemCount) {
    return new LearnerMemoryCodeReviewUpdateResult(
        LearnerMemoryCodeReviewUpdateResult.Status.FAILED, updateRunId, windowProblemCount, 0);
  }

  private static List<Long> validatedBatchReviewIds(long userId, List<LearnerMemoryCodeReviewFact> batchFacts) {
    if (userId <= 0 || batchFacts == null || batchFacts.size() != LearnerMemoryCodeReviewConsumerConstants.BATCH_SIZE
        || batchFacts.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Code review memory batch must contain exactly five reviews");
    }
    List<Long> reviewIds = batchFacts.stream().map(LearnerMemoryCodeReviewFact::reviewId).sorted().toList();
    if (new HashSet<>(reviewIds).size() != LearnerMemoryCodeReviewConsumerConstants.BATCH_SIZE) {
      throw new IllegalArgumentException("Code review memory batch review ids must be distinct");
    }
    return reviewIds;
  }

  private static String attemptIdempotencyKey(String logicalIdempotencyKey, int attempt) {
    if (attempt < 0) {
      throw new IllegalArgumentException("Code review memory attempt must not be negative");
    }
    return attempt == 0 ? logicalIdempotencyKey
        : logicalIdempotencyKey + LearnerMemoryCodeReviewConsumerConstants.BACKGROUND_RETRY_IDEMPOTENCY_KEY_SEPARATOR
            + attempt;
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 must be available", exception);
    }
  }

  private static long requiredPositiveLong(Map<String, Object> metadata, String key) {
    Object value = metadata.get(key);
    try {
      long parsed = value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value).trim());
      if (parsed <= 0) {
        throw new IllegalArgumentException("Code review memory Agent run id must be positive");
      }
      return parsed;
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Code review memory Agent run id is unavailable", exception);
    }
  }

  private record DecisionRound(JsonNode structuredOutput, long agentRunId, int toolCallCount) {
  }
}
