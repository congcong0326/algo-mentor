package org.congcong.algomentor.mentor.application.profile.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.mentor.application.profile.LearnerMemoryClaimDimensionCatalog;
import org.congcong.algomentor.mentor.application.profile.LearnerMemoryClaimDimension;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimQueryService;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimSnapshot;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimMessageEvidence;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceReferences;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceValidationContext;
import org.congcong.algomentor.mentor.application.profile.evidence.repository.LearnerMemoryEvidenceRepository;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperation;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperationBatch;
import org.congcong.algomentor.mentor.application.profile.operation.service.LearnerMemoryAtomicApplyService;
import org.congcong.algomentor.mentor.application.profile.operation.service.LearnerMemoryOperationFailure;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRun;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRunDraft;
import org.congcong.algomentor.mentor.application.profile.run.repository.LearnerMemoryUpdateRunRepository;
import org.congcong.algomentor.mentor.application.profile.run.service.LearnerMemoryUpdateRunLifecycleService;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateRequest;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateResult;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerDeclaredProfileToolContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 受信消息驱动的用户自述 Claim 更新编排；模型调用始终发生在数据库事务外。 */
public class DeclaredProfileUpdateService implements DeclaredProfileUpdateHandler {

  private static final Logger log = LoggerFactory.getLogger(DeclaredProfileUpdateService.class);

  private final LearnerMemoryClaimQueryService claimQueryService;
  private final LearnerMemoryEvidenceRepository evidenceRepository;
  private final LearnerMemoryUpdateRunRepository updateRunRepository;
  private final LearnerMemoryAtomicApplyService atomicApplyService;
  private final LearnerMemoryUpdateRunLifecycleService runLifecycleService;
  private final AgentTurnMessageLookupRepository turnMessageLookupRepository;
  private final AgentRuntime agentRuntime;
  private final DeclaredProfileUpdatePromptBuilder promptBuilder;
  private final int maxStaleRetries;
  private final int resultSummaryMaxChars;
  private final LearnerMemoryMetrics metrics;

  public DeclaredProfileUpdateService(
      LearnerMemoryClaimQueryService claimQueryService,
      LearnerMemoryEvidenceRepository evidenceRepository,
      LearnerMemoryUpdateRunRepository updateRunRepository,
      LearnerMemoryAtomicApplyService atomicApplyService,
      LearnerMemoryUpdateRunLifecycleService runLifecycleService,
      AgentTurnMessageLookupRepository turnMessageLookupRepository,
      AgentRuntime agentRuntime,
      DeclaredProfileUpdatePromptBuilder promptBuilder,
      int maxStaleRetries,
      int resultSummaryMaxChars) {
    this(
        claimQueryService, evidenceRepository, updateRunRepository, atomicApplyService, runLifecycleService,
        turnMessageLookupRepository, agentRuntime, promptBuilder, maxStaleRetries, resultSummaryMaxChars,
        LearnerMemoryMetrics.NOOP);
  }

  public DeclaredProfileUpdateService(
      LearnerMemoryClaimQueryService claimQueryService,
      LearnerMemoryEvidenceRepository evidenceRepository,
      LearnerMemoryUpdateRunRepository updateRunRepository,
      LearnerMemoryAtomicApplyService atomicApplyService,
      LearnerMemoryUpdateRunLifecycleService runLifecycleService,
      AgentTurnMessageLookupRepository turnMessageLookupRepository,
      AgentRuntime agentRuntime,
      DeclaredProfileUpdatePromptBuilder promptBuilder,
      int maxStaleRetries,
      int resultSummaryMaxChars,
      LearnerMemoryMetrics metrics) {
    this.claimQueryService = Objects.requireNonNull(claimQueryService, "claimQueryService");
    this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "evidenceRepository");
    this.updateRunRepository = Objects.requireNonNull(updateRunRepository, "updateRunRepository");
    this.atomicApplyService = Objects.requireNonNull(atomicApplyService, "atomicApplyService");
    this.runLifecycleService = Objects.requireNonNull(runLifecycleService, "runLifecycleService");
    this.turnMessageLookupRepository = Objects.requireNonNull(turnMessageLookupRepository, "turnMessageLookupRepository");
    this.agentRuntime = Objects.requireNonNull(agentRuntime, "agentRuntime");
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
    if (maxStaleRetries < 0 || maxStaleRetries > 1 || resultSummaryMaxChars < 1) {
      throw new IllegalArgumentException("Invalid declared profile update properties");
    }
    this.maxStaleRetries = maxStaleRetries;
    this.resultSummaryMaxChars = resultSummaryMaxChars;
    this.metrics = metrics == null ? LearnerMemoryMetrics.NOOP : metrics;
  }

  public DeclaredProfileUpdateResult update(
      long userId,
      DeclaredProfileUpdateRequest request,
      long parentRunDbId,
      int parentStepIndex) {
    Objects.requireNonNull(request, "request");
    if (userId < 1 || parentRunDbId < 1 || parentStepIndex < 1) {
      return failed(request);
    }
    LearnerMemoryUpdateRun updateRun = null;
    try {
      String idempotencyKey = childIdempotencyKey(parentRunDbId, parentStepIndex, request);
      updateRun = findOrCreateRun(userId, request, idempotencyKey);
      if (updateRun.status().isTerminal()) {
        return terminalResult(request, updateRun.status());
      }
      TrustedMessage message = trustedCurrentMessage(userId, parentRunDbId);
      LearnerMemoryClaimSnapshot snapshot = claimQueryService.snapshot(userId);
      Long retryOfRunId = null;
      for (int attempt = 0; attempt <= maxStaleRetries; attempt++) {
        DecisionRound round = decide(
            userId, request, snapshot, message, parentRunDbId, parentStepIndex,
            attemptIdempotencyKey(idempotencyKey, attempt), retryOfRunId);
        updateRunRepository.bindAgentRun(updateRun.id(), round.agentRunId());
        List<Decision> decisions = parseOperations(round.structuredOutput(), round.candidates());
        PreparedBatch prepared = prepareBatch(
            userId,
            updateRun.id(),
            snapshot,
            message,
            decisions);
        LearnerMemoryAtomicApplyService.ApplyResult result = atomicApplyService.apply(prepared.batch(), prepared.context());
        if (result.status() == LearnerMemoryAtomicApplyService.ApplyStatus.STALE) {
          if (attempt == maxStaleRetries) {
            runLifecycleService.markFailed(userId, updateRun.id(), 0, LearnerMemoryOperationFailure.Code.STALE_SNAPSHOT);
            metrics.recordInvalidOutput("STALE");
            metrics.recordUpdateRun(LearnerMemoryRunContract.Trigger.DECLARED_FACT, LearnerMemoryRunContract.Status.FAILED);
            return failed(request);
          }
          retryOfRunId = round.agentRunId();
          snapshot = claimQueryService.snapshot(userId);
          continue;
        }
        metrics.recordUpdateRun(
            LearnerMemoryRunContract.Trigger.DECLARED_FACT,
            result.status() == LearnerMemoryAtomicApplyService.ApplyStatus.APPLIED
                ? LearnerMemoryRunContract.Status.SUCCEEDED
                : LearnerMemoryRunContract.Status.NO_CHANGE);
        return result(request, decisions, result.status());
      }
    } catch (LearnerMemoryOperationFailure failure) {
      metrics.recordInvalidOutput("VALIDATION");
      log.info("Declared memory operation rejected. code={}", failure.code());
      if (updateRun != null) {
        runLifecycleService.markFailed(userId, updateRun.id(), 0, failure.code());
        metrics.recordUpdateRun(LearnerMemoryRunContract.Trigger.DECLARED_FACT, LearnerMemoryRunContract.Status.FAILED);
      }
    } catch (IllegalArgumentException exception) {
      log.info(
          "Declared memory agent output rejected. failureCode={}",
          LearnerMemoryOperationFailure.Code.INVALID_AGENT_OUTPUT);
      if (updateRun != null) {
        runLifecycleService.markFailed(
            userId, updateRun.id(), 0, LearnerMemoryOperationFailure.Code.INVALID_AGENT_OUTPUT);
        metrics.recordInvalidOutput("SCHEMA");
        metrics.recordUpdateRun(LearnerMemoryRunContract.Trigger.DECLARED_FACT, LearnerMemoryRunContract.Status.FAILED);
      }
    } catch (RuntimeException exception) {
      log.warn("Declared memory update failed. exceptionType={}", exception.getClass().getSimpleName());
      if (updateRun != null) {
        runLifecycleService.markFailed(
            userId, updateRun.id(), 0, LearnerMemoryOperationFailure.Code.AGENT_FAILURE);
        metrics.recordInvalidOutput("TOOL_FAILURE");
        metrics.recordUpdateRun(LearnerMemoryRunContract.Trigger.DECLARED_FACT, LearnerMemoryRunContract.Status.FAILED);
      }
    }
    return failed(request);
  }

  static String childIdempotencyKey(
      long parentRunDbId,
      int parentStepIndex,
      DeclaredProfileUpdateRequest request) {
    if (parentRunDbId < 1 || parentStepIndex < 1) {
      throw new IllegalArgumentException("Declared profile child parent run and step must be positive");
    }
    String summary = Objects.requireNonNull(request, "request").updates().stream()
        .sorted(Comparator.comparing(item -> item.dimension().name()))
        .map(item -> item.dimension().name() + '\u001f' + item.intent().name() + '\u001f'
            + normalizeStatement(item.statement()))
        .collect(java.util.stream.Collectors.joining("\u001e"));
    return LearnerDeclaredProfileToolContracts.CHILD_IDEMPOTENCY_KEY_PREFIX
        + sha256(parentRunDbId + "\u001d" + parentStepIndex + "\u001d"
            + LearnerDeclaredProfileToolContracts.TOOL_NAME + "\u001d" + summary);
  }

  private LearnerMemoryUpdateRun findOrCreateRun(
      long userId,
      DeclaredProfileUpdateRequest request,
      String idempotencyKey) {
    return updateRunRepository.findByIdempotencyKey(idempotencyKey).map(existing -> {
      if (existing.userId() != userId || existing.trigger() != LearnerMemoryRunContract.Trigger.DECLARED_FACT) {
        throw new IllegalStateException("Declared memory update run does not match trusted context");
      }
      return existing;
    }).orElseGet(() -> updateRunRepository.create(new LearnerMemoryUpdateRunDraft(
        userId,
        LearnerMemoryRunContract.Trigger.DECLARED_FACT,
        idempotencyKey,
        LearnerDeclaredProfileToolContracts.PROMPT_VERSION,
        LearnerDeclaredProfileToolContracts.SCHEMA_VERSION,
        request.updates().size(),
        Instant.now())));
  }

  private TrustedMessage trustedCurrentMessage(long userId, long parentRunDbId) {
    AgentTurnMessages turn = turnMessageLookupRepository.findByRunId(parentRunDbId)
        .filter(messages -> messages.runId() == parentRunDbId)
        .orElseThrow(() -> new IllegalStateException("Declared memory current turn is unavailable"));
    AgentMessage message = turn.userMessage();
    if (!evidenceRepository.findOwnedMessageIds(userId, Set.of(message.id())).contains(message.id())) {
      throw new IllegalStateException("Declared memory current message ownership is invalid");
    }
    return new TrustedMessage(message.id(), message.content(), message.createdAt());
  }

  private DecisionRound decide(
      long userId,
      DeclaredProfileUpdateRequest request,
      LearnerMemoryClaimSnapshot snapshot,
      TrustedMessage message,
      long parentRunDbId,
      int parentStepIndex,
      String idempotencyKey,
      Long retryOfRunId) {
    List<DeclaredProfileUpdateAgentInput.Candidate> candidates = candidates(request, snapshot, message.content());
    AgentInvocation<DeclaredProfileUpdateAgentInput> invocation = new AgentInvocation<>(
        DeclaredProfileUpdateAgentDefinition.KEY,
        new DeclaredProfileUpdateAgentInput(userId, candidates, idempotencyKey, retryOfRunId),
        new AgentInvocationContext(
            userId, AgentInvocationMode.CHILD, idempotencyKey, Long.toString(parentRunDbId), parentStepIndex,
            message.content().length(), false));
    AgentRunResult result = agentRuntime.execute(invocation);
    long agentRunId = requiredPositiveLong(result.metadata(), AgentRuntimeMetadataKeys.RUN_DB_ID);
    return new DecisionRound(
        candidates,
        result.output() == null ? null : result.output().structured(),
        agentRunId);
  }

  private List<DeclaredProfileUpdateAgentInput.Candidate> candidates(
      DeclaredProfileUpdateRequest request,
      LearnerMemoryClaimSnapshot snapshot,
      String currentMessage) {
    return request.updates().stream().map(item -> {
      LearnerMemoryClaimContract.Dimension dimension = memoryDimension(item.dimension());
      List<DeclaredProfileUpdateAgentInput.ActiveClaim> active = snapshot.activeClaims().stream()
          .filter(claim -> claim.scope().kind() == LearnerMemoryClaimContract.Kind.DECLARED_FACT
              && claim.scope().dimension() == dimension)
          .map(claim -> new DeclaredProfileUpdateAgentInput.ActiveClaim(claim.id(), claim.claimText()))
          .toList();
      return new DeclaredProfileUpdateAgentInput.Candidate(
          item.dimension(), currentMessage, item.intent(), active);
    }).toList();
  }

  private PreparedBatch prepareBatch(
      long userId,
      long updateRunId,
      LearnerMemoryClaimSnapshot snapshot,
      TrustedMessage currentMessage,
      List<Decision> decisions) {
    Map<Long, LearnerMemoryClaimRevision> activeById = snapshot.activeClaims().stream()
        .collect(java.util.stream.Collectors.toMap(LearnerMemoryClaimRevision::id, claim -> claim));
    Set<Long> targetIds = decisions.stream().filter(TargetDecision.class::isInstance)
        .map(TargetDecision.class::cast).map(TargetDecision::targetRevisionId).collect(java.util.stream.Collectors.toSet());
    Map<Long, List<LearnerMemoryClaimMessageEvidence>> existing = evidenceRepository
        .findMessageEvidenceByRevisionIds(userId, targetIds).stream()
        .collect(java.util.stream.Collectors.groupingBy(LearnerMemoryClaimMessageEvidence::claimRevisionId));
    Map<Long, LearnerMemoryEvidenceValidationContext.MessageSource> sources = new LinkedHashMap<>();
    sources.put(currentMessage.id(), new LearnerMemoryEvidenceValidationContext.MessageSource(
        currentMessage.id(), currentMessage.createdAt()));
    List<LearnerMemoryOperation> operations = new ArrayList<>();
    for (Decision decision : decisions) {
      if (decision instanceof AddDecision add) {
        operations.add(new LearnerMemoryOperation.Add(
            declaredScope(add.dimension()), add.claimText(), declaration(currentMessage.id()), null));
        continue;
      }
      TargetDecision target = (TargetDecision) decision;
      LearnerMemoryClaimRevision current = activeById.get(target.targetRevisionId());
      if (current == null || current.scope().kind() != LearnerMemoryClaimContract.Kind.DECLARED_FACT) {
        throw new IllegalArgumentException("Declared memory target is not an active declared claim");
      }
      LearnerMemoryEvidenceReferences evidence = correction(
          existing.getOrDefault(current.id(), List.of()), currentMessage, sources);
      if (target instanceof ReviseDecision revise) {
        operations.add(new LearnerMemoryOperation.Revise(current.id(), revise.claimText(), evidence, null));
      } else {
        operations.add(new LearnerMemoryOperation.Retire(current.id(), evidence, null));
      }
    }
    return new PreparedBatch(
        new LearnerMemoryOperationBatch(userId, updateRunId, snapshot.token(), 0, operations),
        new LearnerMemoryEvidenceValidationContext(List.of(), List.copyOf(sources.values())));
  }

  private LearnerMemoryEvidenceReferences declaration(long messageId) {
    return new LearnerMemoryEvidenceReferences(
        LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION,
        List.of(),
        List.of(new LearnerMemoryEvidenceReferences.MessageReference(
            messageId, LearnerMemoryEvidenceContract.MessageRole.DECLARED)));
  }

  private LearnerMemoryEvidenceReferences correction(
      List<LearnerMemoryClaimMessageEvidence> existing,
      TrustedMessage currentMessage,
      Map<Long, LearnerMemoryEvidenceValidationContext.MessageSource> sources) {
    Map<Long, LearnerMemoryEvidenceContract.MessageRole> messages = new LinkedHashMap<>();
    existing.stream().sorted(Comparator.comparingInt(LearnerMemoryClaimMessageEvidence::sequenceNo)
        .thenComparingLong(LearnerMemoryClaimMessageEvidence::messageId)).forEach(value -> {
          messages.put(value.messageId(), value.role());
          sources.putIfAbsent(value.messageId(), new LearnerMemoryEvidenceValidationContext.MessageSource(
              value.messageId(), value.createdAt()));
        });
    LearnerMemoryEvidenceContract.MessageRole previous = messages.put(currentMessage.id(),
        LearnerMemoryEvidenceContract.MessageRole.CORRECTED);
    if (previous != null && previous != LearnerMemoryEvidenceContract.MessageRole.CORRECTED) {
      throw new IllegalArgumentException("Declared memory correction message role conflicts with existing evidence");
    }
    return new LearnerMemoryEvidenceReferences(
        LearnerMemoryEvidenceContract.Pattern.USER_CORRECTION,
        List.of(),
        messages.entrySet().stream().map(entry -> new LearnerMemoryEvidenceReferences.MessageReference(
            entry.getKey(), entry.getValue())).toList());
  }

  private List<Decision> parseOperations(
      JsonNode structuredOutput,
      List<DeclaredProfileUpdateAgentInput.Candidate> candidates) {
    if (structuredOutput == null || !structuredOutput.isObject() || structuredOutput.size() != 1
        || !structuredOutput.has(DeclaredProfileUpdateJsonSchema.OPERATIONS)) {
      throw new IllegalArgumentException("Declared memory structured output is invalid");
    }
    JsonNode values = structuredOutput.path(DeclaredProfileUpdateJsonSchema.OPERATIONS);
    if (!values.isArray() || values.size() > 10) {
      throw new IllegalArgumentException("Declared memory operation count is invalid");
    }
    Map<LearnerMemoryClaimDimension, org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent>
        intentsByDimension = candidates.stream().collect(java.util.stream.Collectors.toMap(
            DeclaredProfileUpdateAgentInput.Candidate::dimension,
            DeclaredProfileUpdateAgentInput.Candidate::intent));
    Map<Long, TargetPermission> targets = new HashMap<>();
    candidates.forEach(candidate -> candidate.activeClaims().forEach(claim ->
        targets.put(claim.revisionId(), new TargetPermission(candidate.dimension(), candidate.intent()))));
    List<Decision> result = new ArrayList<>();
    for (JsonNode value : values) {
      String action = requiredText(value, DeclaredProfileUpdateJsonSchema.OPERATION_ACTION);
      result.add(switch (action) {
        case "ADD" -> parseAdd(value, intentsByDimension);
        case "REVISE" -> parseRevise(value, targets);
        case "RETIRE" -> parseRetire(value, targets);
        default -> throw new IllegalArgumentException("Declared memory operation action is not allowed");
      });
    }
    return List.copyOf(result);
  }

  private AddDecision parseAdd(
      JsonNode value,
      Map<LearnerMemoryClaimDimension, org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent>
          intents) {
    requireFields(value, Set.of(
        DeclaredProfileUpdateJsonSchema.OPERATION_ACTION,
        DeclaredProfileUpdateJsonSchema.OPERATION_DIMENSION,
        DeclaredProfileUpdateJsonSchema.OPERATION_CLAIM_TEXT));
    LearnerMemoryClaimDimension dimension = enumValue(
        value.path(DeclaredProfileUpdateJsonSchema.OPERATION_DIMENSION), LearnerMemoryClaimDimension.class);
    if (intents.get(dimension) != org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent.DECLARE
        || !LearnerMemoryClaimDimensionCatalog.declaredDimensions().contains(dimension)) {
      throw new IllegalArgumentException("Declared memory ADD dimension is not allowed");
    }
    return new AddDecision(dimension, requiredText(value, DeclaredProfileUpdateJsonSchema.OPERATION_CLAIM_TEXT));
  }

  private ReviseDecision parseRevise(JsonNode value, Map<Long, TargetPermission> targets) {
    requireFields(value, Set.of(
        DeclaredProfileUpdateJsonSchema.OPERATION_ACTION,
        DeclaredProfileUpdateJsonSchema.OPERATION_TARGET_REVISION_ID,
        DeclaredProfileUpdateJsonSchema.OPERATION_CLAIM_TEXT));
    long target = requiredPositiveLong(value.path(DeclaredProfileUpdateJsonSchema.OPERATION_TARGET_REVISION_ID));
    TargetPermission permission = targets.get(target);
    if (permission == null
        || permission.intent() != org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent.CORRECT) {
      throw new IllegalArgumentException("Declared memory REVISE target is not allowed");
    }
    return new ReviseDecision(
        target,
        permission.dimension(),
        requiredText(value, DeclaredProfileUpdateJsonSchema.OPERATION_CLAIM_TEXT));
  }

  private RetireDecision parseRetire(JsonNode value, Map<Long, TargetPermission> targets) {
    requireFields(value, Set.of(
        DeclaredProfileUpdateJsonSchema.OPERATION_ACTION,
        DeclaredProfileUpdateJsonSchema.OPERATION_TARGET_REVISION_ID));
    long target = requiredPositiveLong(value.path(DeclaredProfileUpdateJsonSchema.OPERATION_TARGET_REVISION_ID));
    TargetPermission permission = targets.get(target);
    if (permission == null
        || permission.intent() != org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent.CORRECT) {
      throw new IllegalArgumentException("Declared memory RETIRE target is not allowed");
    }
    return new RetireDecision(target, permission.dimension());
  }

  private static void requireFields(JsonNode value, Set<String> expected) {
    if (!value.isObject() || value.size() != expected.size()) {
      throw new IllegalArgumentException("Declared memory operation fields are invalid");
    }
    LinkedHashSet<String> actual = new LinkedHashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException("Declared memory operation has unsupported fields");
    }
  }

  private static String requiredText(JsonNode value, String field) {
    JsonNode node = value.path(field);
    if (!node.isTextual() || node.asText().isBlank()) {
      throw new IllegalArgumentException("Declared memory operation text is invalid");
    }
    return node.asText().trim();
  }

  private static long requiredPositiveLong(JsonNode node) {
    if (!node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() <= 0) {
      throw new IllegalArgumentException("Declared memory target revision is invalid");
    }
    return node.asLong();
  }

  private static long requiredPositiveLong(Map<String, Object> metadata, String key) {
    Object value = metadata.get(key);
    try {
      long parsed = value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value).trim());
      if (parsed <= 0) {
        throw new IllegalArgumentException("Declared memory child run id is invalid");
      }
      return parsed;
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Declared memory child run id is unavailable", exception);
    }
  }

  private static <T extends Enum<T>> T enumValue(JsonNode node, Class<T> type) {
    if (!node.isTextual()) {
      throw new IllegalArgumentException("Declared memory operation enum is invalid");
    }
    try {
      return Enum.valueOf(type, node.asText());
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Declared memory operation enum is not allowed", exception);
    }
  }

  private DeclaredProfileUpdateResult result(
      DeclaredProfileUpdateRequest request,
      List<Decision> decisions,
      LearnerMemoryAtomicApplyService.ApplyStatus status) {
    boolean changed = status == LearnerMemoryAtomicApplyService.ApplyStatus.APPLIED;
    Map<LearnerMemoryClaimDimension, String> summaries = new HashMap<>();
    Set<LearnerMemoryClaimDimension> changedDimensions = new java.util.HashSet<>();
    for (Decision decision : decisions) {
      if (decision instanceof AddDecision add) {
        changedDimensions.add(add.dimension());
        summaries.put(add.dimension(), summarize(add.claimText()));
      } else if (decision instanceof ReviseDecision revise) {
        changedDimensions.add(revise.dimension());
        summaries.put(revise.dimension(), summarize(revise.claimText()));
      } else if (decision instanceof RetireDecision retire) {
        changedDimensions.add(retire.dimension());
      }
    }
    List<DeclaredProfileUpdateResult.Item> items = request.updates().stream().map(item ->
        new DeclaredProfileUpdateResult.Item(
            item.dimension(),
            changed && changedDimensions.contains(item.dimension())
                ? DeclaredProfileUpdateResult.ItemStatus.APPLIED
                : DeclaredProfileUpdateResult.ItemStatus.NO_CHANGE,
            summaries.getOrDefault(item.dimension(), ""))).toList();
    return new DeclaredProfileUpdateResult(
        changed ? DeclaredProfileUpdateResult.Status.UPDATED : DeclaredProfileUpdateResult.Status.NO_CHANGE,
        changed ? LearnerDeclaredProfileToolContracts.MESSAGE_UPDATED : LearnerDeclaredProfileToolContracts.MESSAGE_NO_CHANGE,
        items);
  }

  private DeclaredProfileUpdateResult terminalResult(
      DeclaredProfileUpdateRequest request,
      LearnerMemoryRunContract.Status status) {
    if (status == LearnerMemoryRunContract.Status.FAILED) {
      return failed(request);
    }
    boolean changed = status == LearnerMemoryRunContract.Status.SUCCEEDED;
    List<DeclaredProfileUpdateResult.Item> items = request.updates().stream().map(item ->
        new DeclaredProfileUpdateResult.Item(
            item.dimension(),
            changed ? DeclaredProfileUpdateResult.ItemStatus.APPLIED : DeclaredProfileUpdateResult.ItemStatus.NO_CHANGE,
            "")).toList();
    return new DeclaredProfileUpdateResult(
        changed ? DeclaredProfileUpdateResult.Status.UPDATED : DeclaredProfileUpdateResult.Status.NO_CHANGE,
        changed ? LearnerDeclaredProfileToolContracts.MESSAGE_UPDATED : LearnerDeclaredProfileToolContracts.MESSAGE_NO_CHANGE,
        items);
  }

  private DeclaredProfileUpdateResult failed(DeclaredProfileUpdateRequest request) {
    return DeclaredProfileUpdateResult.failed(request.updates().stream()
        .map(DeclaredProfileUpdateRequest.Item::dimension).toList());
  }

  private String summarize(String text) {
    String normalized = text == null ? "" : text.trim();
    return normalized.length() <= resultSummaryMaxChars ? normalized : normalized.substring(0, resultSummaryMaxChars);
  }

  private static LearnerMemoryClaimScope declaredScope(LearnerMemoryClaimDimension dimension) {
    return new LearnerMemoryClaimScope(
        LearnerMemoryClaimContract.Kind.DECLARED_FACT, memoryDimension(dimension), null);
  }

  private static LearnerMemoryClaimContract.Dimension memoryDimension(LearnerMemoryClaimDimension dimension) {
    return LearnerMemoryClaimContract.Dimension.valueOf(dimension.name());
  }

  private static String attemptIdempotencyKey(String logicalIdempotencyKey, int attempt) {
    return attempt == 0 ? logicalIdempotencyKey
        : logicalIdempotencyKey + LearnerDeclaredProfileToolContracts.CHILD_RETRY_IDEMPOTENCY_KEY_SEPARATOR + attempt;
  }

  private static String normalizeStatement(String statement) {
    return statement.trim().replaceAll("\\s+", " ");
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 must be available", exception);
    }
  }

  private sealed interface Decision permits AddDecision, TargetDecision {
  }

  private record AddDecision(LearnerMemoryClaimDimension dimension, String claimText) implements Decision {
  }

  private sealed interface TargetDecision extends Decision permits ReviseDecision, RetireDecision {
    long targetRevisionId();

    LearnerMemoryClaimDimension dimension();
  }

  private record ReviseDecision(
      long targetRevisionId,
      LearnerMemoryClaimDimension dimension,
      String claimText) implements TargetDecision {
  }

  private record RetireDecision(long targetRevisionId, LearnerMemoryClaimDimension dimension) implements TargetDecision {
  }

  private record DecisionRound(
      List<DeclaredProfileUpdateAgentInput.Candidate> candidates,
      JsonNode structuredOutput,
      long agentRunId) {
  }

  private record TrustedMessage(long id, String content, Instant createdAt) {
  }

  private record PreparedBatch(
      LearnerMemoryOperationBatch batch,
      LearnerMemoryEvidenceValidationContext context) {
  }

  private record TargetPermission(
      LearnerMemoryClaimDimension dimension,
      org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent intent) {
  }
}
