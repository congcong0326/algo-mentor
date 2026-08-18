package org.congcong.algomentor.mentor.application.profile.operation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevisionDraft;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimSnapshot;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimSnapshotFactory;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimTextHasher;
import org.congcong.algomentor.mentor.application.profile.claim.repository.LearnerMemoryClaimRepository;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimMessageEvidence;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimReviewEvidence;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceReferences;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceValidationContext;
import org.congcong.algomentor.mentor.application.profile.evidence.repository.LearnerMemoryEvidenceRepository;
import org.congcong.algomentor.mentor.application.profile.evidence.service.LearnerMemoryEvidenceGradeCalculator;
import org.congcong.algomentor.mentor.application.profile.evidence.service.LearnerMemoryEvidenceValidator;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperation;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperationBatch;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRun;
import org.congcong.algomentor.mentor.application.profile.run.repository.LearnerMemoryUpdateRunRepository;
import org.congcong.algomentor.mentor.application.profile.run.service.LearnerMemoryUpdateRunLifecycleService;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 统一 claim operation 的短事务应用内核。模型调用和来源查询必须在调用本服务前完成。
 */
public final class LearnerMemoryAtomicApplyService {

  private static final Comparator<LearnerMemoryClaimScope> SCOPE_ORDER = Comparator
      .comparing((LearnerMemoryClaimScope scope) -> scope.kind().ordinal())
      .thenComparing(scope -> scope.dimension().ordinal())
      .thenComparing(LearnerMemoryClaimScope::tagId, Comparator.nullsFirst(Long::compareTo));

  private final LearnerMemoryClaimRepository claimRepository;
  private final LearnerMemoryEvidenceRepository evidenceRepository;
  private final LearnerMemoryUpdateRunRepository updateRunRepository;
  private final LearnerMemoryClaimTextHasher textHasher;
  private final LearnerMemoryClaimSnapshotFactory snapshotFactory;
  private final LearnerMemoryEvidenceValidator evidenceValidator;
  private final LearnerMemoryEvidenceGradeCalculator gradeCalculator;
  private final LearnerMemoryUpdateRunLifecycleService runLifecycleService;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;
  private final LearnerMemoryMetrics metrics;

  public LearnerMemoryAtomicApplyService(
      LearnerMemoryClaimRepository claimRepository,
      LearnerMemoryEvidenceRepository evidenceRepository,
      LearnerMemoryUpdateRunRepository updateRunRepository,
      LearnerMemoryClaimTextHasher textHasher,
      LearnerMemoryClaimSnapshotFactory snapshotFactory,
      LearnerMemoryEvidenceValidator evidenceValidator,
      LearnerMemoryEvidenceGradeCalculator gradeCalculator,
      LearnerMemoryUpdateRunLifecycleService runLifecycleService,
      TransactionTemplate transactionTemplate) {
    this(
        claimRepository, evidenceRepository, updateRunRepository, textHasher, snapshotFactory, evidenceValidator,
        gradeCalculator, runLifecycleService, transactionTemplate, Clock.systemUTC(), LearnerMemoryMetrics.NOOP);
  }

  public LearnerMemoryAtomicApplyService(
      LearnerMemoryClaimRepository claimRepository,
      LearnerMemoryEvidenceRepository evidenceRepository,
      LearnerMemoryUpdateRunRepository updateRunRepository,
      LearnerMemoryClaimTextHasher textHasher,
      LearnerMemoryClaimSnapshotFactory snapshotFactory,
      LearnerMemoryEvidenceValidator evidenceValidator,
      LearnerMemoryEvidenceGradeCalculator gradeCalculator,
      LearnerMemoryUpdateRunLifecycleService runLifecycleService,
      TransactionTemplate transactionTemplate,
      Clock clock) {
    this(
        claimRepository, evidenceRepository, updateRunRepository, textHasher, snapshotFactory, evidenceValidator,
        gradeCalculator, runLifecycleService, transactionTemplate, clock, LearnerMemoryMetrics.NOOP);
  }

  public LearnerMemoryAtomicApplyService(
      LearnerMemoryClaimRepository claimRepository,
      LearnerMemoryEvidenceRepository evidenceRepository,
      LearnerMemoryUpdateRunRepository updateRunRepository,
      LearnerMemoryClaimTextHasher textHasher,
      LearnerMemoryClaimSnapshotFactory snapshotFactory,
      LearnerMemoryEvidenceValidator evidenceValidator,
      LearnerMemoryEvidenceGradeCalculator gradeCalculator,
      LearnerMemoryUpdateRunLifecycleService runLifecycleService,
      TransactionTemplate transactionTemplate,
      Clock clock,
      LearnerMemoryMetrics metrics) {
    this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
    this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "evidenceRepository");
    this.updateRunRepository = Objects.requireNonNull(updateRunRepository, "updateRunRepository");
    this.textHasher = Objects.requireNonNull(textHasher, "textHasher");
    this.snapshotFactory = Objects.requireNonNull(snapshotFactory, "snapshotFactory");
    this.evidenceValidator = Objects.requireNonNull(evidenceValidator, "evidenceValidator");
    this.gradeCalculator = Objects.requireNonNull(gradeCalculator, "gradeCalculator");
    this.runLifecycleService = Objects.requireNonNull(runLifecycleService, "runLifecycleService");
    this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.metrics = metrics == null ? LearnerMemoryMetrics.NOOP : metrics;
  }

  public ApplyResult apply(
      LearnerMemoryOperationBatch batch,
      LearnerMemoryEvidenceValidationContext evidenceContext) {
    Objects.requireNonNull(batch, "batch");
    Objects.requireNonNull(evidenceContext, "evidenceContext");
    try {
      ApplyResult result = transactionTemplate.execute(status -> applyLocked(batch, evidenceContext));
      if (result == null) {
        throw new IllegalStateException("Learner memory apply transaction returned no result");
      }
      recordCommittedApply(result);
      return result;
    } catch (LearnerMemoryOperationFailure failure) {
      if (failure.code() != LearnerMemoryOperationFailure.Code.STALE_SNAPSHOT
          && failure.code() != LearnerMemoryOperationFailure.Code.INVALID_EVIDENCE) {
        runLifecycleService.markFailed(batch, failure.code());
      }
      throw failure;
    }
  }

  private ApplyResult applyLocked(
      LearnerMemoryOperationBatch batch,
      LearnerMemoryEvidenceValidationContext evidenceContext) {
    claimRepository.lockUser(batch.userId());
    LearnerMemoryUpdateRun run = updateRunRepository.findById(batch.updateRunId())
        .filter(candidate -> candidate.userId() == batch.userId()
            && candidate.status() == LearnerMemoryRunContract.Status.RUNNING)
        .orElseThrow(() -> failure(LearnerMemoryOperationFailure.Code.UPDATE_RUN_INVALID));
    LearnerMemoryClaimSnapshot before = snapshotFactory.create(claimRepository.findActiveByUserForUpdate(batch.userId()));
    if (!before.token().equals(batch.expectedSnapshotToken())) {
      return new ApplyResult(ApplyStatus.STALE, before, 0, List.of());
    }
    if (batch.operations().isEmpty()) {
      updateRunRepository.complete(
          run.id(), LearnerMemoryRunContract.Status.NO_CHANGE, 0, batch.toolCallCount(), null, clock.instant());
      return new ApplyResult(ApplyStatus.NO_CHANGE, before, 0, List.of());
    }

    List<PreparedOperation> prepared = prepareAll(batch, before.activeClaims(), evidenceContext);
    verifyOwnership(batch.userId(), prepared);
    Instant now = clock.instant();
    List<LearnerMemoryClaimReviewEvidence> reviewWrites = new ArrayList<>();
    List<LearnerMemoryClaimMessageEvidence> messageWrites = new ArrayList<>();
    for (PreparedOperation operation : prepared) {
      LearnerMemoryClaimRevision inserted = writeRevision(batch, operation, now);
      reviewWrites.addAll(reviewWrites(inserted.id(), operation.references(), evidenceContext, now));
      messageWrites.addAll(messageWrites(inserted.id(), operation.references(), evidenceContext, now));
    }
    evidenceRepository.insertReviewEvidence(reviewWrites);
    evidenceRepository.insertMessageEvidence(messageWrites);
    updateRunRepository.complete(
        run.id(), LearnerMemoryRunContract.Status.SUCCEEDED, prepared.size(), batch.toolCallCount(), null, now);
    LearnerMemoryClaimSnapshot after = snapshotFactory.create(claimRepository.findActiveByUser(batch.userId()));
    return new ApplyResult(ApplyStatus.APPLIED, after, prepared.size(), prepared.stream()
        .map(operation -> new OperationObservation(
            operation.operation().action(),
            operation.scope().kind(),
            operation.scope().dimension(),
            operation.references().pattern().name(),
            operation.grade().name(),
            operation.references().reviews().size() + operation.references().messages().size()))
        .toList());
  }

  private void recordCommittedApply(ApplyResult result) {
    if (result.status() != ApplyStatus.APPLIED) {
      return;
    }
    for (OperationObservation operation : result.operations()) {
      metrics.recordOperation(operation.action(), operation.kind(), operation.dimension());
      metrics.recordEvidence(operation.pattern(), operation.grade(), operation.evidenceCount());
    }
    result.snapshot().activeClaims().stream()
        .collect(Collectors.groupingBy(claim -> claim.scope().kind().name() + ":" + claim.scope().dimension().name()))
        .forEach((ignored, claims) -> {
          LearnerMemoryClaimRevision claim = claims.get(0);
          metrics.recordActiveClaimCount(claim.scope().kind(), claim.scope().dimension(), claims.size());
        });
  }

  private List<PreparedOperation> prepareAll(
      LearnerMemoryOperationBatch batch,
      List<LearnerMemoryClaimRevision> active,
      LearnerMemoryEvidenceValidationContext evidenceContext) {
    Map<Long, LearnerMemoryClaimRevision> activeById = active.stream().collect(Collectors.toMap(
        LearnerMemoryClaimRevision::id,
        claim -> claim));
    Map<Long, ExistingEvidence> existing = loadExistingEvidence(batch.userId(), batch.operations());
    List<OperationWithScope> ordered = batch.operations().stream()
        .map(operation -> new OperationWithScope(operation, scopeFor(operation, activeById)))
        .sorted(Comparator.comparing(OperationWithScope::scope, SCOPE_ORDER)
            .thenComparingLong(value -> value.operation().targetId() == null ? 0L : value.operation().targetId()))
        .toList();
    ActiveState capacity = new ActiveState(active);
    List<PreparedOperation> result = new ArrayList<>();
    for (OperationWithScope value : ordered) {
      LearnerMemoryOperation operation = value.operation();
      LearnerMemoryClaimRevision current = operation.targetId() == null ? null : activeById.get(operation.targetId());
      if (operation.targetId() != null && current == null) {
        throw failure(LearnerMemoryOperationFailure.Code.INVALID_OPERATION);
      }
      LearnerMemoryEvidenceReferences references = operation instanceof LearnerMemoryOperation.Confirm
          ? mergeEvidence(operation.evidence(), existing.get(current.id()))
          : operation.evidence();
      LearnerMemoryClaimContract.Origin origin = validateOriginAndEvidence(value.scope(), references, evidenceContext);
      String text = normalizedText(operation, current);
      String hash = operation instanceof LearnerMemoryOperation.Confirm
          ? current.claimTextHash()
          : textHasher.hash(text).value();
      LearnerMemoryClaimContract.RevisionStatus status = operation instanceof LearnerMemoryOperation.Retire
          ? LearnerMemoryClaimContract.RevisionStatus.RETIRED
          : LearnerMemoryClaimContract.RevisionStatus.ACTIVE;
      PreparedOperation prepared = new PreparedOperation(
          operation, current, value.scope(), text, hash, references,
          gradeCalculator.calculate(references, evidenceContext), status, origin,
          operation instanceof LearnerMemoryOperation.Add ? UUID.randomUUID() : current.claimKey());
      capacity.apply(prepared);
      result.add(prepared);
    }
    return List.copyOf(result);
  }

  private Map<Long, ExistingEvidence> loadExistingEvidence(long userId, List<LearnerMemoryOperation> operations) {
    List<Long> targetIds = operations.stream()
        .filter(LearnerMemoryOperation.Confirm.class::isInstance)
        .map(LearnerMemoryOperation::targetId)
        .toList();
    if (targetIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, List<LearnerMemoryClaimReviewEvidence>> reviews = evidenceRepository
        .findReviewEvidenceByRevisionIds(userId, targetIds).stream()
        .collect(Collectors.groupingBy(LearnerMemoryClaimReviewEvidence::claimRevisionId));
    Map<Long, List<LearnerMemoryClaimMessageEvidence>> messages = evidenceRepository
        .findMessageEvidenceByRevisionIds(userId, targetIds).stream()
        .collect(Collectors.groupingBy(LearnerMemoryClaimMessageEvidence::claimRevisionId));
    Map<Long, ExistingEvidence> result = new HashMap<>();
    for (Long targetId : targetIds) {
      result.put(targetId, new ExistingEvidence(
          reviews.getOrDefault(targetId, List.of()), messages.getOrDefault(targetId, List.of())));
    }
    return result;
  }

  private LearnerMemoryClaimScope scopeFor(
      LearnerMemoryOperation operation,
      Map<Long, LearnerMemoryClaimRevision> activeById) {
    if (operation instanceof LearnerMemoryOperation.Add add) {
      return add.scope();
    }
    LearnerMemoryClaimRevision current = activeById.get(operation.targetId());
    if (current == null) {
      throw failure(LearnerMemoryOperationFailure.Code.INVALID_OPERATION);
    }
    return current.scope();
  }

  private LearnerMemoryClaimContract.Origin validateOriginAndEvidence(
      LearnerMemoryClaimScope scope,
      LearnerMemoryEvidenceReferences references,
      LearnerMemoryEvidenceValidationContext evidenceContext) {
    LearnerMemoryClaimContract.Origin origin = switch (references.pattern()) {
      case USER_DECLARATION -> LearnerMemoryClaimContract.Origin.USER_EXPLICIT;
      case USER_CORRECTION -> LearnerMemoryClaimContract.Origin.USER_CORRECTION;
      default -> LearnerMemoryClaimContract.Origin.SYSTEM_DERIVED;
    };
    boolean declared = scope.kind() == LearnerMemoryClaimContract.Kind.DECLARED_FACT;
    if ((declared && origin == LearnerMemoryClaimContract.Origin.SYSTEM_DERIVED)
        || (!declared && origin != LearnerMemoryClaimContract.Origin.SYSTEM_DERIVED)) {
      throw failure(LearnerMemoryOperationFailure.Code.INVALID_OPERATION);
    }
    evidenceValidator.validate(references, scope, evidenceContext);
    return origin;
  }

  private String normalizedText(LearnerMemoryOperation operation, LearnerMemoryClaimRevision current) {
    if (operation instanceof LearnerMemoryOperation.Confirm || operation instanceof LearnerMemoryOperation.Retire) {
      return current.claimText();
    }
    String raw = operation instanceof LearnerMemoryOperation.Add add ? add.claimText()
        : ((LearnerMemoryOperation.Revise) operation).claimText();
    String normalized = textHasher.normalize(raw);
    if (normalized.length() > LearnerMemoryClaimContract.CLAIM_TEXT_MAX_CHARS) {
      throw failure(LearnerMemoryOperationFailure.Code.INVALID_OPERATION);
    }
    return normalized;
  }

  private void verifyOwnership(long userId, Collection<PreparedOperation> operations) {
    Set<Long> reviewIds = operations.stream().flatMap(operation -> operation.references().reviews().stream())
        .map(LearnerMemoryEvidenceReferences.ReviewReference::reviewId)
        .collect(Collectors.toSet());
    Set<Long> messageIds = operations.stream().flatMap(operation -> operation.references().messages().stream())
        .map(LearnerMemoryEvidenceReferences.MessageReference::messageId)
        .collect(Collectors.toSet());
    if (!evidenceRepository.findOwnedReviewIds(userId, reviewIds).containsAll(reviewIds)
        || !evidenceRepository.findOwnedMessageIds(userId, messageIds).containsAll(messageIds)) {
      throw failure(LearnerMemoryOperationFailure.Code.INVALID_EVIDENCE);
    }
  }

  private LearnerMemoryClaimRevision writeRevision(
      LearnerMemoryOperationBatch batch,
      PreparedOperation operation,
      Instant now) {
    LearnerMemoryClaimRevision current = operation.current();
    if (current != null) {
      claimRepository.markCurrentSuperseded(current.id(), now);
    }
    return claimRepository.insert(new LearnerMemoryClaimRevisionDraft(
        operation.claimKey(),
        batch.userId(),
        operation.scope(),
        current == null ? 1 : current.revisionNo() + 1,
        operation.status(),
        operation.claimText(),
        new org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimTextHash(operation.claimTextHash()),
        operation.origin(),
        operation.references().pattern(),
        operation.grade(),
        operation.operation().decisionReason(),
        batch.updateRunId(),
        current == null ? null : current.id(),
        now,
        null));
  }

  private List<LearnerMemoryClaimReviewEvidence> reviewWrites(
      long revisionId,
      LearnerMemoryEvidenceReferences references,
      LearnerMemoryEvidenceValidationContext context,
      Instant now) {
    List<LearnerMemoryEvidenceReferences.ReviewReference> ordered = references.reviews().stream()
        .sorted(Comparator.comparing(
            (LearnerMemoryEvidenceReferences.ReviewReference reference) -> context.review(reference.reviewId()).createdAt())
            .thenComparingLong(LearnerMemoryEvidenceReferences.ReviewReference::reviewId))
        .toList();
    List<LearnerMemoryClaimReviewEvidence> result = new ArrayList<>();
    for (int index = 0; index < ordered.size(); index++) {
      LearnerMemoryEvidenceReferences.ReviewReference reference = ordered.get(index);
      result.add(new LearnerMemoryClaimReviewEvidence(
          revisionId, reference.reviewId(), reference.role(), index + 1, now));
    }
    return result;
  }

  private List<LearnerMemoryClaimMessageEvidence> messageWrites(
      long revisionId,
      LearnerMemoryEvidenceReferences references,
      LearnerMemoryEvidenceValidationContext context,
      Instant now) {
    List<LearnerMemoryEvidenceReferences.MessageReference> ordered = references.messages().stream()
        .sorted(Comparator.comparing(
            (LearnerMemoryEvidenceReferences.MessageReference reference) -> context.message(reference.messageId()).createdAt())
            .thenComparingLong(LearnerMemoryEvidenceReferences.MessageReference::messageId))
        .toList();
    List<LearnerMemoryClaimMessageEvidence> result = new ArrayList<>();
    for (int index = 0; index < ordered.size(); index++) {
      LearnerMemoryEvidenceReferences.MessageReference reference = ordered.get(index);
      result.add(new LearnerMemoryClaimMessageEvidence(
          revisionId, reference.messageId(), reference.role(), index + 1, now));
    }
    return result;
  }

  private LearnerMemoryEvidenceReferences mergeEvidence(
      LearnerMemoryEvidenceReferences newEvidence,
      ExistingEvidence existing) {
    if (existing == null) {
      throw failure(LearnerMemoryOperationFailure.Code.INVALID_OPERATION);
    }
    Map<Long, LearnerMemoryEvidenceContract.ReviewRole> reviews = new HashMap<>();
    Map<Long, LearnerMemoryEvidenceContract.MessageRole> messages = new HashMap<>();
    existing.reviews().forEach(value -> reviews.put(value.reviewId(), value.role()));
    existing.messages().forEach(value -> messages.put(value.messageId(), value.role()));
    for (LearnerMemoryEvidenceReferences.ReviewReference value : newEvidence.reviews()) {
      if (reviews.putIfAbsent(value.reviewId(), value.role()) != null && reviews.get(value.reviewId()) != value.role()) {
        throw failure(LearnerMemoryOperationFailure.Code.INVALID_EVIDENCE);
      }
    }
    for (LearnerMemoryEvidenceReferences.MessageReference value : newEvidence.messages()) {
      if (messages.putIfAbsent(value.messageId(), value.role()) != null && messages.get(value.messageId()) != value.role()) {
        throw failure(LearnerMemoryOperationFailure.Code.INVALID_EVIDENCE);
      }
    }
    return new LearnerMemoryEvidenceReferences(
        newEvidence.pattern(),
        reviews.entrySet().stream().map(entry -> new LearnerMemoryEvidenceReferences.ReviewReference(
            entry.getKey(), entry.getValue())).toList(),
        messages.entrySet().stream().map(entry -> new LearnerMemoryEvidenceReferences.MessageReference(
            entry.getKey(), entry.getValue())).toList());
  }

  private static LearnerMemoryOperationFailure failure(LearnerMemoryOperationFailure.Code code) {
    return new LearnerMemoryOperationFailure(code);
  }

  public enum ApplyStatus {
    APPLIED,
    NO_CHANGE,
    STALE
  }

  public record ApplyResult(
      ApplyStatus status,
      LearnerMemoryClaimSnapshot snapshot,
      int operationCount,
      List<OperationObservation> operations
  ) {
    public ApplyResult {
      if (status == null || snapshot == null || operationCount < 0 || operations == null) {
        throw new IllegalArgumentException("learner memory apply result 非法。");
      }
      operations = List.copyOf(operations);
    }
  }

  public record OperationObservation(
      LearnerMemoryClaimContract.OperationAction action,
      LearnerMemoryClaimContract.Kind kind,
      LearnerMemoryClaimContract.Dimension dimension,
      String pattern,
      String grade,
      int evidenceCount
  ) {
  }

  private record ExistingEvidence(
      List<LearnerMemoryClaimReviewEvidence> reviews,
      List<LearnerMemoryClaimMessageEvidence> messages) {
  }

  private record OperationWithScope(LearnerMemoryOperation operation, LearnerMemoryClaimScope scope) {
  }

  private record PreparedOperation(
      LearnerMemoryOperation operation,
      LearnerMemoryClaimRevision current,
      LearnerMemoryClaimScope scope,
      String claimText,
      String claimTextHash,
      LearnerMemoryEvidenceReferences references,
      LearnerMemoryEvidenceContract.Grade grade,
      LearnerMemoryClaimContract.RevisionStatus status,
      LearnerMemoryClaimContract.Origin origin,
      UUID claimKey) {
  }

  private static final class ActiveState {
    private long total;
    private final Map<LearnerMemoryClaimScope, Integer> counts = new HashMap<>();
    private final Map<LearnerMemoryClaimScope, Set<String>> hashes = new HashMap<>();

    private ActiveState(List<LearnerMemoryClaimRevision> claims) {
      claims.forEach(this::add);
    }

    private void apply(PreparedOperation operation) {
      if (operation.current() != null) {
        remove(operation.current());
      }
      if (operation.status() == LearnerMemoryClaimContract.RevisionStatus.ACTIVE) {
        if (operation.current() == null && total >= LearnerMemoryClaimContract.USER_ACTIVE_HARD_LIMIT) {
          throw failure(LearnerMemoryOperationFailure.Code.HARD_LIMIT);
        }
        int limit = switch (operation.scope().kind()) {
          case DECLARED_FACT -> LearnerMemoryClaimContract.DECLARED_SCOPE_ACTIVE_LIMIT;
          case GENERAL_OBSERVATION -> LearnerMemoryClaimContract.GENERAL_SCOPE_ACTIVE_LIMIT;
          case TAG_ASSESSMENT -> LearnerMemoryClaimContract.TAG_SCOPE_ACTIVE_LIMIT;
        };
        if (operation.current() == null && counts.getOrDefault(operation.scope(), 0) >= limit) {
          throw failure(LearnerMemoryOperationFailure.Code.SCOPE_LIMIT);
        }
        Set<String> scopeHashes = hashes.computeIfAbsent(operation.scope(), ignored -> new HashSet<>());
        if (!scopeHashes.add(operation.claimTextHash())) {
          throw failure(LearnerMemoryOperationFailure.Code.DUPLICATE_ACTIVE_TEXT);
        }
        total++;
        counts.merge(operation.scope(), 1, Integer::sum);
      }
    }

    private void add(LearnerMemoryClaimRevision claim) {
      total++;
      counts.merge(claim.scope(), 1, Integer::sum);
      hashes.computeIfAbsent(claim.scope(), ignored -> new HashSet<>()).add(claim.claimTextHash());
    }

    private void remove(LearnerMemoryClaimRevision claim) {
      total--;
      counts.compute(claim.scope(), (scope, count) -> count == null || count <= 1 ? null : count - 1);
      Set<String> scopeHashes = hashes.get(claim.scope());
      if (scopeHashes != null) {
        scopeHashes.remove(claim.claimTextHash());
        if (scopeHashes.isEmpty()) {
          hashes.remove(claim.scope());
        }
      }
    }
  }
}
