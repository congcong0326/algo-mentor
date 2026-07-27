package org.congcong.algomentor.mentor.application.profile.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.ai.governance.completion.AiCompletionContext;
import org.congcong.algomentor.ai.governance.completion.AiCompletionGateway;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
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
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMetadataKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 事务外执行批量模型判定，随后调用画像短事务完成全有或全无更新。 */
public class DeclaredProfileUpdateService {

  private static final Logger log = LoggerFactory.getLogger(DeclaredProfileUpdateService.class);

  private final LearnerProfileQueryService queryService;
  private final LearnerProfileUpdateService updateService;
  private final AiCompletionGateway completionGateway;
  private final DeclaredProfileUpdatePromptBuilder promptBuilder;
  private final int maxStaleRetries;
  private final int resultSummaryMaxChars;

  public DeclaredProfileUpdateService(
      LearnerProfileQueryService queryService,
      LearnerProfileUpdateService updateService,
      AiCompletionGateway completionGateway,
      DeclaredProfileUpdatePromptBuilder promptBuilder,
      int maxStaleRetries,
      int resultSummaryMaxChars
  ) {
    this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    this.updateService = Objects.requireNonNull(updateService, "updateService must not be null");
    this.completionGateway = Objects.requireNonNull(completionGateway, "completionGateway must not be null");
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
      AiCompletionContext completionContext
  ) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(completionContext, "completionContext must not be null");
    if (userId < 1) {
      return failed(request);
    }
    try {
      List<CandidateState> candidates = loadCandidates(userId, request);
      for (int attempt = 0; attempt <= maxStaleRetries; attempt++) {
        DecisionRound round = decide(userId, candidates, completionContext);
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

  private DecisionRound decide(long userId, List<CandidateState> candidates, AiCompletionContext completionContext) {
    ResolvedSystemPromptSnapshot promptSnapshot = promptBuilder.snapshot(userId);
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(LearnerDeclaredProfileToolContracts.METADATA_DIMENSION_COUNT, candidates.size());
    metadata.putAll(SystemPromptMetadataKeys.from(promptSnapshot));
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.requiring(Set.of(LlmCapability.JSON_SCHEMA_OUTPUT)))
        .messages(promptBuilder.build(candidates.stream().map(candidate -> new DeclaredProfileUpdatePromptBuilder.Candidate(
            candidate.update().dimension(),
            candidate.update().statement(),
            candidate.update().intent(),
            candidate.currentContent())).toList(), promptSnapshot))
        .responseFormat(new LlmResponseFormat.JsonSchema(
            DeclaredProfileUpdateJsonSchema.SCHEMA_NAME,
            DeclaredProfileUpdateJsonSchema.schema(),
            true))
        .metadata(Map.copyOf(metadata))
        .build();
    log.info("Declared profile AI decision started. dimensions={} stepIndex={}",
        candidates.size(), completionContext.stepIndex());
    LlmCompletionResult completion = completionGateway.complete(request, completionContext);
    List<ProfileUpdateDecision> decisions = parseDecisions(completion.structuredOutput(), candidates);
    log.info("Declared profile AI decision completed. dimensions={} provider={} model={} actions={}",
        candidates.size(), completion.provider().value(), completion.model().value(),
        decisions.stream().map(decision -> decision.action().name()).toList());
    return new DecisionRound(decisions, completion.provider().value(), completion.model().value());
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

  private record DecisionRound(List<ProfileUpdateDecision> decisions, String provider, String model) {
  }
}
