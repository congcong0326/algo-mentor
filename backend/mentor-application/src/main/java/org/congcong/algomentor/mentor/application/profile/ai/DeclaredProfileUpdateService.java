package org.congcong.algomentor.mentor.application.profile.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
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
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryKind;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileIdentity;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileQueryService;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileSnapshot;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateAction;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateApplyResult;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateApplyStatus;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateCommand;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateDecision;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateRequest;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateResult;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerDeclaredProfileToolContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 事务外执行批量模型判定，随后调用画像短事务完成全有或全无更新。 */
public class DeclaredProfileUpdateService {

  private static final Logger log = LoggerFactory.getLogger(DeclaredProfileUpdateService.class);

  private final LearnerProfileQueryService queryService;
  private final LearnerProfileUpdateService updateService;
  private final AgentRuntime agentRuntime;
  private final DeclaredProfileUpdatePromptBuilder promptBuilder;
  private final int maxStaleRetries;
  private final int resultSummaryMaxChars;

  public DeclaredProfileUpdateService(
      LearnerProfileQueryService queryService,
      LearnerProfileUpdateService updateService,
      AgentRuntime agentRuntime,
      DeclaredProfileUpdatePromptBuilder promptBuilder,
      int maxStaleRetries,
      int resultSummaryMaxChars
  ) {
    this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    this.updateService = Objects.requireNonNull(updateService, "updateService must not be null");
    this.agentRuntime = Objects.requireNonNull(agentRuntime, "Agent runtime must not be null");
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder must not be null");
    if (maxStaleRetries < 0 || maxStaleRetries > 1 || resultSummaryMaxChars < 1) {
      throw new IllegalArgumentException("Invalid declared profile update properties");
    }
    this.maxStaleRetries = maxStaleRetries;
    this.resultSummaryMaxChars = resultSummaryMaxChars;
  }

  public DeclaredProfileUpdateResult update(
      long userId,
      DeclaredProfileUpdateRequest request,
      long parentRunDbId,
      int parentStepIndex
  ) {
    Objects.requireNonNull(request, "request must not be null");
    if (userId < 1 || parentRunDbId < 1 || parentStepIndex < 1) {
      return failed(request);
    }
    try {
      List<CandidateState> candidates = loadCandidates(userId, request);
      String logicalIdempotencyKey = childIdempotencyKey(parentRunDbId, parentStepIndex, request);
      Long retryOfRunId = null;
      for (int attempt = 0; attempt <= maxStaleRetries; attempt++) {
        DecisionRound round = decide(
            userId,
            candidates,
            parentRunDbId,
            parentStepIndex,
            attemptIdempotencyKey(logicalIdempotencyKey, attempt),
            retryOfRunId);
        List<ProfileUpdateApplyResult> applied = updateService.applyBatch(commands(candidates, round));
        if (applied.size() != candidates.size()) {
          throw new IllegalStateException("Declared profile update returned an unexpected result count");
        }
        if (applied.stream().anyMatch(result -> result.status() == ProfileUpdateApplyStatus.STALE)) {
          log.info("Declared profile update became stale. userId={} dimensions={} retryAttempt={}",
              userId, candidates.size(), attempt);
          if (attempt == maxStaleRetries) {
            return failed(request);
          }
          retryOfRunId = round.runDbId();
          candidates = loadCandidates(userId, request);
          continue;
        }
        return result(request, applied);
      }
    } catch (RuntimeException exception) {
      log.warn("Declared profile update failed. userId={} dimensions={} exceptionType={}",
          userId, request.updates().size(), exception.getClass().getSimpleName());
    }
    return failed(request);
  }

  private List<CandidateState> loadCandidates(long userId, DeclaredProfileUpdateRequest request) {
    return request.updates().stream().map(update -> {
      LearnerProfileIdentity identity = LearnerProfileIdentity.dimension(
          userId, LearnerProfileEntryKind.DECLARED_FACT, update.dimension());
      LearnerProfileSnapshot snapshot = queryService.snapshot(identity);
      String currentContent = snapshot.currentEntry().map(entry -> entry.contentText()).orElse("");
      return new CandidateState(update, snapshot, currentContent);
    }).toList();
  }

  private DecisionRound decide(
      long userId,
      List<CandidateState> candidates,
      long parentRunDbId,
      int parentStepIndex,
      String idempotencyKey,
      Long retryOfRunId
  ) {
    AgentInvocation<DeclaredProfileUpdateAgentInput> invocation = new AgentInvocation<>(
        DeclaredProfileUpdateAgentDefinition.KEY,
        new DeclaredProfileUpdateAgentInput(
            userId,
            candidates.stream().map(candidate -> new DeclaredProfileUpdateAgentInput.Candidate(
                candidate.update().dimension(),
                candidate.update().statement(),
                candidate.update().intent(),
                candidate.currentContent())).toList(),
            idempotencyKey,
            retryOfRunId),
        new AgentInvocationContext(
            userId,
            AgentInvocationMode.CHILD,
            idempotencyKey,
            Long.toString(parentRunDbId),
            parentStepIndex,
            requestSize(candidates),
            false));
    log.info("Declared profile child Agent request started. dimensions={} parentRunDbId={} parentStepIndex={} retry={}",
        candidates.size(), parentRunDbId, parentStepIndex, retryOfRunId != null);
    AgentRunResult result = agentRuntime.execute(invocation);
    List<ProfileUpdateDecision> decisions = parseDecisions(
        result.output() == null ? null : result.output().structured(), candidates);
    String provider = metadataText(result.metadata(), AgentRuntimeMetadataKeys.RUNTIME_PROVIDER);
    String model = metadataText(result.metadata(), AgentRuntimeMetadataKeys.RUNTIME_MODEL);
    long runDbId = requiredPositiveLong(result.metadata(), AgentRuntimeMetadataKeys.RUN_DB_ID);
    log.info("Declared profile child Agent request completed. dimensions={} parentRunDbId={} parentStepIndex={} runDbId={} provider={} model={} actions={}",
        candidates.size(), parentRunDbId, parentStepIndex, runDbId, provider, model,
        decisions.stream().map(decision -> decision.action().name()).toList());
    return new DecisionRound(decisions, provider, model, runDbId);
  }

  static String childIdempotencyKey(
      long parentRunDbId,
      int parentStepIndex,
      DeclaredProfileUpdateRequest request
  ) {
    if (parentRunDbId < 1 || parentStepIndex < 1) {
      throw new IllegalArgumentException("Declared profile child parent run and step must be positive");
    }
    DeclaredProfileUpdateRequest candidate = Objects.requireNonNull(request, "request must not be null");
    String summary = candidate.updates().stream()
        .sorted(java.util.Comparator.comparing(item -> item.dimension().name()))
        .map(item -> item.dimension().name() + '\u001f' + item.intent().name() + '\u001f'
            + normalizeStatement(item.statement()))
        .collect(java.util.stream.Collectors.joining("\u001e"));
    return LearnerDeclaredProfileToolContracts.CHILD_IDEMPOTENCY_KEY_PREFIX
        + sha256(parentRunDbId + "\u001d" + parentStepIndex + "\u001d"
            + LearnerDeclaredProfileToolContracts.TOOL_NAME + "\u001d" + summary);
  }

  private static String attemptIdempotencyKey(String logicalIdempotencyKey, int attempt) {
    if (attempt < 0) {
      throw new IllegalArgumentException("Declared profile child attempt must not be negative");
    }
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

  private static int requestSize(List<CandidateState> candidates) {
    return candidates.stream().mapToInt(candidate -> candidate.update().statement().length()).sum();
  }

  private static String metadataText(Map<String, Object> metadata, String key) {
    Object value = metadata.get(key);
    return value == null ? "" : value.toString();
  }

  private static long requiredPositiveLong(Map<String, Object> metadata, String key) {
    Object value = metadata.get(key);
    try {
      long parsed = value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value).trim());
      if (parsed < 1) {
        throw new IllegalArgumentException("Declared profile Runtime run id must be positive");
      }
      return parsed;
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Declared profile Runtime result is missing run id", exception);
    }
  }

  private List<ProfileUpdateDecision> parseDecisions(JsonNode structuredOutput, List<CandidateState> candidates) {
    if (structuredOutput == null || !structuredOutput.isObject() || structuredOutput.size() != 1
        || !structuredOutput.has(DeclaredProfileUpdateJsonSchema.DECISIONS)) {
      throw new IllegalArgumentException("Declared profile structured output is invalid");
    }
    JsonNode items = structuredOutput.path(DeclaredProfileUpdateJsonSchema.DECISIONS);
    if (!items.isArray() || items.size() != candidates.size()) {
      throw new IllegalArgumentException("Declared profile structured output has an invalid decision count");
    }
    Set<String> expectedFields = Set.of(
        DeclaredProfileUpdateJsonSchema.DECISION_DIMENSION,
        DeclaredProfileUpdateJsonSchema.DECISION_ACTION,
        DeclaredProfileUpdateJsonSchema.DECISION_CONTENT);
    Map<org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension, ProfileUpdateDecision> byDimension =
        new HashMap<>();
    Set<org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension> expectedDimensions = candidates.stream()
        .map(candidate -> candidate.update().dimension()).collect(java.util.stream.Collectors.toSet());
    for (JsonNode item : items) {
      if (!item.isObject() || item.size() != expectedFields.size()) {
        throw new IllegalArgumentException("Declared profile structured output item is invalid");
      }
      LinkedHashSet<String> fields = new LinkedHashSet<>();
      item.fieldNames().forEachRemaining(fields::add);
      if (!fields.equals(expectedFields)) {
        throw new IllegalArgumentException("Declared profile structured output has unsupported fields");
      }
      org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension dimension = enumValue(
          item.path(DeclaredProfileUpdateJsonSchema.DECISION_DIMENSION),
          org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension.class);
      ProfileUpdateAction action = enumValue(
          item.path(DeclaredProfileUpdateJsonSchema.DECISION_ACTION), ProfileUpdateAction.class);
      JsonNode contentNode = item.path(DeclaredProfileUpdateJsonSchema.DECISION_CONTENT);
      if (!contentNode.isTextual() || !expectedDimensions.contains(dimension)) {
        throw new IllegalArgumentException("Declared profile structured output content is invalid");
      }
      String content = contentNode.asText().trim();
      if (action == ProfileUpdateAction.REPLACE && content.isBlank()) {
        throw new IllegalArgumentException("Declared profile replacement content is blank");
      }
      if (byDimension.putIfAbsent(dimension, new ProfileUpdateDecision(action,
          action == ProfileUpdateAction.REPLACE ? content : null,
          "declared profile decision")) != null) {
        throw new IllegalArgumentException("Declared profile structured output has duplicate dimensions");
      }
    }
    if (byDimension.size() != candidates.size()) {
      throw new IllegalArgumentException("Declared profile structured output has missing dimensions");
    }
    return candidates.stream().map(candidate -> byDimension.get(candidate.update().dimension())).toList();
  }

  private <T extends Enum<T>> T enumValue(JsonNode node, Class<T> type) {
    if (!node.isTextual()) {
      throw new IllegalArgumentException("Declared profile structured output enum is invalid");
    }
    try {
      return Enum.valueOf(type, node.asText());
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Declared profile structured output enum is not allowed", exception);
    }
  }

  private List<ProfileUpdateCommand> commands(List<CandidateState> candidates, DecisionRound round) {
    List<ProfileUpdateCommand> commands = new ArrayList<>();
    for (int index = 0; index < candidates.size(); index++) {
      CandidateState candidate = candidates.get(index);
      commands.add(new ProfileUpdateCommand(
          candidate.snapshot().identity(),
          round.decisions().get(index),
          candidate.snapshot().snapshotToken(),
          candidate.update().intent().originType(),
          round.provider(),
          round.model(),
          LearnerDeclaredProfileToolContracts.PROMPT_VERSION));
    }
    return List.copyOf(commands);
  }

  private DeclaredProfileUpdateResult result(
      DeclaredProfileUpdateRequest request,
      List<ProfileUpdateApplyResult> applied
  ) {
    boolean changed = applied.stream().anyMatch(result -> result.status() == ProfileUpdateApplyStatus.APPLIED);
    List<DeclaredProfileUpdateResult.Item> items = new ArrayList<>();
    for (int index = 0; index < applied.size(); index++) {
      ProfileUpdateApplyResult update = applied.get(index);
      DeclaredProfileUpdateResult.ItemStatus status = update.status() == ProfileUpdateApplyStatus.APPLIED
          ? DeclaredProfileUpdateResult.ItemStatus.APPLIED
          : DeclaredProfileUpdateResult.ItemStatus.NO_CHANGE;
      String summary = update.currentEntry().map(entry -> summarize(entry.contentText())).orElse("");
      items.add(new DeclaredProfileUpdateResult.Item(request.updates().get(index).dimension(), status, summary));
    }
    return new DeclaredProfileUpdateResult(
        changed ? DeclaredProfileUpdateResult.Status.UPDATED : DeclaredProfileUpdateResult.Status.NO_CHANGE,
        changed ? LearnerDeclaredProfileToolContracts.MESSAGE_UPDATED : LearnerDeclaredProfileToolContracts.MESSAGE_NO_CHANGE,
        items);
  }

  private String summarize(String content) {
    String normalized = content == null ? "" : content.trim();
    if (normalized.length() <= resultSummaryMaxChars) {
      return normalized;
    }
    return normalized.substring(0, resultSummaryMaxChars);
  }

  private DeclaredProfileUpdateResult failed(DeclaredProfileUpdateRequest request) {
    return DeclaredProfileUpdateResult.failed(request.updates().stream()
        .map(DeclaredProfileUpdateRequest.Item::dimension).toList());
  }

  private record CandidateState(
      DeclaredProfileUpdateRequest.Item update,
      LearnerProfileSnapshot snapshot,
      String currentContent
  ) {
  }

  private record DecisionRound(List<ProfileUpdateDecision> decisions, String provider, String model, long runDbId) {
  }
}
