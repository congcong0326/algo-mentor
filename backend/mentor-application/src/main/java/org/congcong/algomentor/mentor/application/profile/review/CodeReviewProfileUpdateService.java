package org.congcong.algomentor.mentor.application.profile.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
import org.congcong.algomentor.mentor.application.profile.LearnerProfileOriginType;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileQueryService;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileSnapshot;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateApplyResult;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateApplyStatus;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateCommand;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateDecision;

/** 事务外模型批量决策，事务内锁用户和 revision 复核；STALE 至多重算一次。 */
public class CodeReviewProfileUpdateService {

  private final CodeReviewProfileFactRepository factRepository;
  private final LearnerProfileQueryService queryService;
  private final LearnerProfileUpdateService updateService;
  private final AgentRuntime agentRuntime;
  private final CodeReviewProfileStructuredOutputMapper outputMapper;
  private final int maxStaleRetries;
  private final CodeReviewProfileMetrics metrics;

  public CodeReviewProfileUpdateService(
      CodeReviewProfileFactRepository factRepository,
      LearnerProfileQueryService queryService,
      LearnerProfileUpdateService updateService,
      AgentRuntime agentRuntime,
      CodeReviewProfileStructuredOutputMapper outputMapper,
      int maxStaleRetries
  ) {
    this(
        factRepository, queryService, updateService, agentRuntime, outputMapper,
        maxStaleRetries, CodeReviewProfileMetrics.NOOP);
  }

  public CodeReviewProfileUpdateService(
      CodeReviewProfileFactRepository factRepository,
      LearnerProfileQueryService queryService,
      LearnerProfileUpdateService updateService,
      AgentRuntime agentRuntime,
      CodeReviewProfileStructuredOutputMapper outputMapper,
      int maxStaleRetries,
      CodeReviewProfileMetrics metrics
  ) {
    if (maxStaleRetries < 0 || maxStaleRetries > CodeReviewProfileConsumerConstants.MAX_STALE_RETRIES) {
      throw new IllegalArgumentException("Code review profile stale retries are invalid");
    }
    this.factRepository = factRepository;
    this.queryService = queryService;
    this.updateService = updateService;
    this.agentRuntime = agentRuntime;
    this.outputMapper = outputMapper;
    this.maxStaleRetries = maxStaleRetries;
    this.metrics = Objects.requireNonNullElse(metrics, CodeReviewProfileMetrics.NOOP);
  }

  public CodeReviewProfileUpdateResult update(long userId, List<CodeReviewProfileFact> batchFacts) {
    try {
      List<CodeReviewProfileFact> window = window(userId, batchFacts);
      if (window.isEmpty()) {
        return new CodeReviewProfileUpdateResult(CodeReviewProfileUpdateResult.Status.FAILED, 0, 0);
      }
      String logicalIdempotencyKey = backgroundIdempotencyKey(userId, batchFacts);
      Long retryOfRunId = null;
      for (int attempt = 0; attempt <= maxStaleRetries; attempt++) {
        List<CodeReviewProfilePromptBuilder.Candidate> candidates = candidates(userId, window);
        DecisionRound round = decide(
            userId,
            window,
            candidates,
            attemptIdempotencyKey(logicalIdempotencyKey, attempt),
            retryOfRunId);
        List<ProfileUpdateApplyResult> results = updateService.applyBatch(commands(candidates, round));
        if (results.size() != candidates.size()) {
          throw new IllegalStateException("Code review profile update returned an unexpected result count");
        }
        if (results.stream().anyMatch(result -> result.status() == ProfileUpdateApplyStatus.STALE)) {
          metrics.recordStaleRetry();
          if (attempt == maxStaleRetries) {
            return new CodeReviewProfileUpdateResult(CodeReviewProfileUpdateResult.Status.FAILED, window.size(), 0);
          }
          retryOfRunId = round.runDbId();
          continue;
        }
        int applied = (int) results.stream().filter(result -> result.status() == ProfileUpdateApplyStatus.APPLIED).count();
        return new CodeReviewProfileUpdateResult(
            applied > 0 ? CodeReviewProfileUpdateResult.Status.UPDATED : CodeReviewProfileUpdateResult.Status.NO_CHANGE,
            window.size(), applied);
      }
      return new CodeReviewProfileUpdateResult(CodeReviewProfileUpdateResult.Status.FAILED, window.size(), 0);
    } catch (RuntimeException exception) {
      return new CodeReviewProfileUpdateResult(CodeReviewProfileUpdateResult.Status.FAILED, 0, 0);
    }
  }

  private List<CodeReviewProfileFact> window(long userId, List<CodeReviewProfileFact> batchFacts) {
    Set<String> batchSlugs = new LinkedHashSet<>();
    for (CodeReviewProfileFact fact : batchFacts == null ? List.<CodeReviewProfileFact>of() : batchFacts) {
      batchSlugs.add(fact.problemSlug());
    }
    if (batchSlugs.isEmpty()) {
      return List.of();
    }
    List<CodeReviewProfileFact> current = new ArrayList<>(
        factRepository.findLatestForProblemSlugs(userId, List.copyOf(batchSlugs)));
    if (current.size() < CodeReviewProfileConsumerConstants.MAX_DISTINCT_PROBLEMS) {
      current.addAll(factRepository.findRecentDistinctProblems(
          userId,
          current.stream().map(CodeReviewProfileFact::problemSlug).toList(),
          CodeReviewProfileConsumerConstants.MAX_DISTINCT_PROBLEMS - current.size()));
    }
    return current.stream().limit(CodeReviewProfileConsumerConstants.MAX_DISTINCT_PROBLEMS).toList();
  }

  private List<CodeReviewProfilePromptBuilder.Candidate> candidates(long userId, List<CodeReviewProfileFact> facts) {
    List<LearnerProfileIdentity> identities = new ArrayList<>();
    CodeReviewProfileConsumerConstants.GENERAL_DIMENSIONS.forEach(dimension -> identities.add(
        LearnerProfileIdentity.dimension(userId, LearnerProfileEntryKind.GENERAL_OBSERVATION, dimension)));
    facts.stream().flatMap(fact -> fact.affectedTagIds().stream()).distinct().sorted().forEach(tagId -> identities.add(
        LearnerProfileIdentity.tagAssessment(userId, tagId)));
    return identities.stream().map(queryService::snapshot).map(snapshot -> new CodeReviewProfilePromptBuilder.Candidate(
        snapshot, snapshot.currentEntry().map(entry -> entry.contentText()).orElse(""))).toList();
  }

  private DecisionRound decide(
      long userId,
      List<CodeReviewProfileFact> facts,
      List<CodeReviewProfilePromptBuilder.Candidate> candidates,
      String idempotencyKey,
      Long retryOfRunId
  ) {
    AgentInvocation<CodeReviewProfileUpdateAgentInput> invocation = new AgentInvocation<>(
        CodeReviewProfileUpdateAgentDefinition.KEY,
        new CodeReviewProfileUpdateAgentInput(userId, facts, candidates, idempotencyKey, retryOfRunId),
        new AgentInvocationContext(
            userId,
            AgentInvocationMode.BACKGROUND,
            idempotencyKey,
            null,
            null,
            facts.size(),
            false));
    AgentRunResult result = agentRuntime.execute(invocation);
    JsonNode output = result.output() == null ? null : result.output().structured();
    List<ProfileUpdateDecision> decisions;
    try {
      decisions = outputMapper.map(output, candidates);
    } catch (IllegalArgumentException exception) {
      metrics.recordInvalidOutput();
      throw exception;
    }
    return new DecisionRound(
        decisions,
        metadataText(result.metadata(), AgentRuntimeMetadataKeys.RUNTIME_PROVIDER),
        metadataText(result.metadata(), AgentRuntimeMetadataKeys.RUNTIME_MODEL),
        requiredPositiveLong(result.metadata(), AgentRuntimeMetadataKeys.RUN_DB_ID));
  }

  static String backgroundIdempotencyKey(long userId, List<CodeReviewProfileFact> batchFacts) {
    if (userId < 1 || batchFacts == null || batchFacts.isEmpty()) {
      throw new IllegalArgumentException("Code review profile background batch must not be empty");
    }
    String reviewIds = batchFacts.stream()
        .map(CodeReviewProfileFact::reviewId)
        .sorted()
        .map(String::valueOf)
        .collect(java.util.stream.Collectors.joining(","));
    return CodeReviewProfileConsumerConstants.BACKGROUND_IDEMPOTENCY_KEY_PREFIX
        + sha256(userId + "\u001d" + reviewIds);
  }

  private static String attemptIdempotencyKey(String logicalIdempotencyKey, int attempt) {
    if (attempt < 0) {
      throw new IllegalArgumentException("Code review profile background attempt must not be negative");
    }
    return attempt == 0 ? logicalIdempotencyKey
        : logicalIdempotencyKey + CodeReviewProfileConsumerConstants.BACKGROUND_RETRY_IDEMPOTENCY_KEY_SEPARATOR
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

  private static String metadataText(Map<String, Object> metadata, String key) {
    Object value = metadata.get(key);
    return value == null ? "" : value.toString();
  }

  private static long requiredPositiveLong(Map<String, Object> metadata, String key) {
    Object value = metadata.get(key);
    try {
      long parsed = value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value).trim());
      if (parsed < 1) {
        throw new IllegalArgumentException("Code review profile Runtime run id must be positive");
      }
      return parsed;
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Code review profile Runtime result is missing run id", exception);
    }
  }

  private List<ProfileUpdateCommand> commands(
      List<CodeReviewProfilePromptBuilder.Candidate> candidates,
      DecisionRound round
  ) {
    List<ProfileUpdateCommand> commands = new ArrayList<>();
    for (int index = 0; index < candidates.size(); index++) {
      LearnerProfileSnapshot snapshot = candidates.get(index).snapshot();
      commands.add(new ProfileUpdateCommand(
          snapshot.identity(), round.decisions().get(index), snapshot.snapshotToken(), LearnerProfileOriginType.SYSTEM_DERIVED,
          round.provider(), round.model(), CodeReviewProfileConsumerConstants.PROMPT_VERSION));
    }
    return List.copyOf(commands);
  }

  private record DecisionRound(List<ProfileUpdateDecision> decisions, String provider, String model, long runDbId) {
  }
}
