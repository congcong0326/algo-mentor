package org.congcong.algomentor.mentor.application.profile.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.ai.governance.completion.AiCompletionContext;
import org.congcong.algomentor.ai.governance.completion.AiCompletionGateway;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
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
import java.util.Set;

/** 事务外模型批量决策，事务内锁用户和 revision 复核；STALE 至多重算一次。 */
public class CodeReviewProfileUpdateService {

  private final CodeReviewProfileFactRepository factRepository;
  private final LearnerProfileQueryService queryService;
  private final LearnerProfileUpdateService updateService;
  private final AiCompletionGateway completionGateway;
  private final CodeReviewProfilePromptBuilder promptBuilder;
  private final CodeReviewProfileStructuredOutputMapper outputMapper;
  private final int maxStaleRetries;
  private final CodeReviewProfileMetrics metrics;

  public CodeReviewProfileUpdateService(
      CodeReviewProfileFactRepository factRepository,
      LearnerProfileQueryService queryService,
      LearnerProfileUpdateService updateService,
      AiCompletionGateway completionGateway,
      CodeReviewProfilePromptBuilder promptBuilder,
      CodeReviewProfileStructuredOutputMapper outputMapper,
      int maxStaleRetries
  ) {
    this(
        factRepository, queryService, updateService, completionGateway, promptBuilder, outputMapper,
        maxStaleRetries, CodeReviewProfileMetrics.NOOP);
  }

  public CodeReviewProfileUpdateService(
      CodeReviewProfileFactRepository factRepository,
      LearnerProfileQueryService queryService,
      LearnerProfileUpdateService updateService,
      AiCompletionGateway completionGateway,
      CodeReviewProfilePromptBuilder promptBuilder,
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
    this.completionGateway = completionGateway;
    this.promptBuilder = promptBuilder;
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
      for (int attempt = 0; attempt <= maxStaleRetries; attempt++) {
        List<CodeReviewProfilePromptBuilder.Candidate> candidates = candidates(userId, window);
        DecisionRound round = decide(userId, window, candidates);
        if (round == null) {
          return new CodeReviewProfileUpdateResult(CodeReviewProfileUpdateResult.Status.FAILED, window.size(), 0);
        }
        List<ProfileUpdateApplyResult> results = updateService.applyBatch(commands(candidates, round));
        if (results.size() != candidates.size()) {
          throw new IllegalStateException("Code review profile update returned an unexpected result count");
        }
        if (results.stream().anyMatch(result -> result.status() == ProfileUpdateApplyStatus.STALE)) {
          metrics.recordStaleRetry();
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
      List<CodeReviewProfilePromptBuilder.Candidate> candidates
  ) {
    AiCompletionContext context = AiCompletionContext.background(
        userId,
        CodeReviewProfileConsumerConstants.AI_PURPOSE,
        AiRunSource.LEARNER_PROFILE_CODE_REVIEW_BATCH,
        CodeReviewProfileConsumerConstants.QUOTA_SCOPE,
        facts.size());
    if (!completionGateway.isAllowed(context)) {
      return null;
    }
    LlmCompletionResult completion = completionGateway.complete(LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.requiring(Set.of(LlmCapability.JSON_SCHEMA_OUTPUT)))
        .messages(promptBuilder.build(facts, candidates))
        .responseFormat(new LlmResponseFormat.JsonSchema(
            CodeReviewProfileJsonSchema.SCHEMA_NAME, CodeReviewProfileJsonSchema.schema(), true))
        .metadata(java.util.Map.of("promptVersion", CodeReviewProfileConsumerConstants.PROMPT_VERSION))
        .build(), context);
    JsonNode output = completion.structuredOutput();
    List<ProfileUpdateDecision> decisions;
    try {
      decisions = outputMapper.map(output, candidates);
    } catch (IllegalArgumentException exception) {
      metrics.recordInvalidOutput();
      throw exception;
    }
    return new DecisionRound(decisions, completion.provider().value(), completion.model().value());
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

  private record DecisionRound(List<ProfileUpdateDecision> decisions, String provider, String model) {
  }
}
