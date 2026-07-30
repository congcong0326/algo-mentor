package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.mentor.application.review.card.PracticeCodeReviewObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Practice Code Review 的领域校验、结构化映射和原子提交入口。 */
public class PracticeCodeReviewService {

  public static final String FAILURE_CODE_LLM_COMPLETION_FAILED = "PRACTICE_CODE_REVIEW_LLM_FAILED";
  public static final String FAILURE_CODE_SAVE_FAILED = "PRACTICE_CODE_REVIEW_SAVE_FAILED";
  public static final String FAILURE_CODE_REPLAY_REVIEW_MISSING = "PRACTICE_CODE_REVIEW_REPLAY_MISSING";

  private static final Logger log = LoggerFactory.getLogger(PracticeCodeReviewService.class);

  private final PracticeCodeReviewRepository repository;
  private final PracticeCodeReviewCommitService commitService;
  private final AgentRuntime agentRuntime;
  private final PracticeCodeReviewStructuredOutputMapper outputMapper;
  private final PracticeCodeReviewMetrics metrics;
  private final PracticeCodeReviewObserver observer;
  private final Function<PracticeTurnContext, PracticeReviewResult> delegate;

  public PracticeCodeReviewService(
      PracticeCodeReviewRepository repository,
      PracticeCodeReviewCommitService commitService,
      AgentRuntime agentRuntime,
      PracticeCodeReviewStructuredOutputMapper outputMapper,
      PracticeCodeReviewMetrics metrics,
      PracticeCodeReviewObserver observer
  ) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.commitService = Objects.requireNonNull(commitService, "commitService must not be null");
    this.agentRuntime = Objects.requireNonNull(agentRuntime, "Agent runtime must not be null");
    this.outputMapper = Objects.requireNonNull(outputMapper, "outputMapper must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.observer = Objects.requireNonNull(observer, "observer must not be null");
    this.delegate = null;
  }

  protected PracticeCodeReviewService(Function<PracticeTurnContext, PracticeReviewResult> delegate) {
    this.repository = null;
    this.commitService = null;
    this.agentRuntime = null;
    this.outputMapper = null;
    this.metrics = PracticeCodeReviewMetrics.NOOP;
    this.observer = PracticeCodeReviewObserver.NOOP;
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
  }

  /**
   * 仅保留给直接领域测试的默认 child 调用入口；生产工具必须传入实际父 step。
   */
  public PracticeReviewResult review(PracticeTurnContext context) {
    PracticeTurnContext candidate = Objects.requireNonNull(context, "context must not be null");
    return review(defaultInvocation(candidate));
  }

  public PracticeReviewResult review(AgentInvocation<PracticeCodeReviewAgentInput> invocation) {
    AgentInvocation<PracticeCodeReviewAgentInput> candidate = Objects.requireNonNull(
        invocation, "Practice code review invocation must not be null");
    validateChildInvocation(candidate);
    PracticeTurnContext context = candidate.input().context();
    if (delegate != null) {
      return delegate.apply(context);
    }

    Optional<PracticeCodeReview> existing = repository.findByUserMessage(
        context.userId(),
        context.sessionId(),
        context.userMessageId());
    if (existing.isPresent()) {
      PracticeCodeReview review = existing.get();
      log.info(
          "Practice code review reused existing review. sessionId={} userMessageId={} reviewId={} versionNo={} agentRunDbId={}",
          context.sessionId(), context.userMessageId(), review.id(), review.versionNo(), review.agentRunDbId());
      PracticeReviewResult result = PracticeReviewResult.saved(review);
      recordReviewResult(result);
      return result;
    }
    log.info(
        "Practice code review existing lookup missed. sessionId={} userMessageId={} parentAgentRunDbId={} problemSlug={}",
        context.sessionId(), context.userMessageId(), context.agentRunDbId(), context.problemSlug());
    PracticeReviewResult result = reviewWithRuntime(context, candidate);
    recordReviewResult(result);
    return result;
  }

  public PracticeReviewResult replay(PracticeTurnContext context) {
    Objects.requireNonNull(context, "context must not be null");
    if (delegate != null) {
      return delegate.apply(context);
    }

    Optional<PracticeCodeReview> existing = repository.findByUserMessage(
        context.userId(), context.sessionId(), context.userMessageId());
    if (existing.isPresent()) {
      PracticeCodeReview review = existing.get();
      log.info(
          "Practice code review replay reused existing review. sessionId={} userMessageId={} reviewId={} versionNo={} agentRunDbId={}",
          context.sessionId(), context.userMessageId(), review.id(), review.versionNo(), review.agentRunDbId());
      PracticeReviewResult result = PracticeReviewResult.saved(review);
      recordReviewResult(result);
      return result;
    }
    log.warn(
        "Practice code review replay missing existing review. sessionId={} userMessageId={} agentRunDbId={} problemSlug={}",
        context.sessionId(), context.userMessageId(), context.agentRunDbId(), context.problemSlug());
    PracticeReviewResult result = PracticeReviewResult.failed(
        FAILURE_CODE_REPLAY_REVIEW_MISSING,
        Map.of("failureCode", FAILURE_CODE_REPLAY_REVIEW_MISSING));
    recordReviewResult(result);
    return result;
  }

  static String childIdempotencyKey(PracticeTurnContext context) {
    PracticeTurnContext candidate = Objects.requireNonNull(context, "context must not be null");
    return PracticeCodeReviewConstants.CHILD_IDEMPOTENCY_KEY_PREFIX
        + candidate.sessionId() + ":" + candidate.userMessageId();
  }

  private AgentInvocation<PracticeCodeReviewAgentInput> defaultInvocation(PracticeTurnContext context) {
    Long parentRunDbId = context.agentRunDbId();
    if (parentRunDbId == null) {
      throw new IllegalArgumentException("Practice code review child invocation requires a parent run database id");
    }
    String idempotencyKey = childIdempotencyKey(context);
    return new AgentInvocation<>(
        PracticeCodeReviewAgentDefinition.KEY,
        new PracticeCodeReviewAgentInput(context, idempotencyKey),
        new AgentInvocationContext(
            context.userId(),
            AgentInvocationMode.CHILD,
            idempotencyKey,
            Long.toString(parentRunDbId),
            1,
            context.originalMessage().length(),
            false));
  }

  private void validateChildInvocation(AgentInvocation<PracticeCodeReviewAgentInput> invocation) {
    if (!PracticeCodeReviewAgentDefinition.KEY.equals(invocation.agentKey())) {
      throw new IllegalArgumentException("Practice code review invocation key must match the Review Definition");
    }
    AgentInvocationContext context = invocation.context();
    PracticeTurnContext turnContext = invocation.input().context();
    if (context.mode() != AgentInvocationMode.CHILD) {
      throw new IllegalArgumentException("Practice code review invocation must use child mode");
    }
    if (context.userId() != turnContext.userId()) {
      throw new IllegalArgumentException("Practice code review invocation user does not match the turn context");
    }
    if (turnContext.agentRunDbId() == null
        || !Long.toString(turnContext.agentRunDbId()).equals(context.parentRunId())
        || context.parentStepIndex() == null) {
      throw new IllegalArgumentException("Practice code review child invocation parent does not match the turn context");
    }
    if (!invocation.input().idempotencyKey().equals(context.idempotencyKey())) {
      throw new IllegalArgumentException("Practice code review invocation idempotency key does not match the input");
    }
  }

  private void recordReviewResult(PracticeReviewResult result) {
    if (result.status() == PracticeReviewStatus.SAVED) {
      metrics.recordReview(PracticeCodeReviewMetricStatus.COMPLETED);
      return;
    }
    if (result.status() == PracticeReviewStatus.FAILED) {
      metrics.recordReview(PracticeCodeReviewMetricStatus.FAILED);
      return;
    }
    metrics.recordReview(PracticeCodeReviewMetricStatus.UNREVIEWABLE);
  }

  private PracticeReviewResult reviewWithRuntime(
      PracticeTurnContext context,
      AgentInvocation<PracticeCodeReviewAgentInput> invocation
  ) {
    RuntimeReviewOutput runtimeOutput;
    try {
      log.info(
          "Practice code review child Agent request started. sessionId={} userMessageId={} parentAgentRunDbId={} parentStepIndex={} problemSlug={} codeProvided={} codeLength={} responseSchema={}",
          context.sessionId(),
          context.userMessageId(),
          context.agentRunDbId(),
          invocation.context().parentStepIndex(),
          context.problemSlug(),
          !context.extractedCode().isBlank(),
          context.extractedCode().length(),
          PracticeCodeReviewConstants.SCHEMA_NAME);
      runtimeOutput = runtimeOutput(agentRuntime.execute(invocation));
    } catch (RuntimeException exception) {
      log.warn(
          "Practice code review child Agent request failed. sessionId={} userMessageId={} parentAgentRunDbId={} exceptionType={}",
          context.sessionId(), context.userMessageId(), context.agentRunDbId(), exception.getClass().getSimpleName(), exception);
      return PracticeReviewResult.failed(
          FAILURE_CODE_LLM_COMPLETION_FAILED,
          Map.of("failureCode", FAILURE_CODE_LLM_COMPLETION_FAILED));
    }

    log.info(
        "Practice code review child Agent request completed. sessionId={} userMessageId={} parentAgentRunDbId={} provider={} model={} finishReason={} usage={} structuredOutput={}",
        context.sessionId(),
        context.userMessageId(),
        context.agentRunDbId(),
        runtimeOutput.provider(),
        runtimeOutput.model(),
        runtimeOutput.finishReason(),
        usageSummary(runtimeOutput.usage()),
        structuredOutputSummary(runtimeOutput.structuredOutput()));

    PracticeReviewResult mapped = outputMapper.map(context, runtimeOutput.structuredOutput());
    log.info(
        "Practice code review child structured output mapped. sessionId={} userMessageId={} parentAgentRunDbId={} status={} failureCode={} draft={}",
        context.sessionId(),
        context.userMessageId(),
        context.agentRunDbId(),
        mapped.status(),
        mapped.failureCode(),
        draftSummary(mapped.draft()));
    if (mapped.status() != PracticeReviewStatus.REVIEWED) {
      log.warn(
          "Practice code review child structured output was not reviewable. sessionId={} userMessageId={} parentAgentRunDbId={} status={} failureCode={}",
          context.sessionId(), context.userMessageId(), context.agentRunDbId(), mapped.status(), mapped.failureCode());
      return mapped;
    }
    return saveReviewedDraft(context, mapped.draft().orElseThrow());
  }

  private RuntimeReviewOutput runtimeOutput(AgentRunResult result) {
    AgentRunResult candidate = Objects.requireNonNull(result, "Practice code review Runtime result must not be null");
    JsonNode structuredOutput = candidate.output() == null ? null : candidate.output().structured();
    Map<String, Object> metadata = candidate.metadata();
    Object usageValue = metadata.get(AgentRuntimeMetadataKeys.RUNTIME_USAGE);
    LlmUsage usage = usageValue instanceof LlmUsage value ? value : LlmUsage.empty();
    return new RuntimeReviewOutput(
        structuredOutput,
        metadataText(metadata, AgentRuntimeMetadataKeys.RUNTIME_PROVIDER),
        metadataText(metadata, AgentRuntimeMetadataKeys.RUNTIME_MODEL),
        usage,
        candidate.finishReason().name());
  }

  private String metadataText(Map<String, Object> metadata, String key) {
    Object value = metadata.get(key);
    return value == null ? "" : value.toString();
  }

  private PracticeReviewResult saveReviewedDraft(PracticeTurnContext context, PracticeCodeReviewDraft draft) {
    try {
      log.info(
          "Practice code review save started. sessionId={} userMessageId={} agentRunDbId={} problemSlug={} language={} rawCodeLength={} normalizedCodeLength={} evidenceCount={} totalScore={} passed={}",
          context.sessionId(), context.userMessageId(), context.agentRunDbId(), draft.problemSlug(), draft.language(),
          draft.rawCode().length(), draft.normalizedCode().length(), draft.evidence().size(),
          draft.score().total().toPlainString(), draft.passed());
      PracticeCodeReviewCommitResult committed = commitService.commit(draft);
      PracticeCodeReview saved = committed.review();
      log.info(
          "Practice code review saved. sessionId={} userMessageId={} agentRunDbId={} reviewId={} versionNo={} totalScore={} passed={}",
          context.sessionId(), context.userMessageId(), context.agentRunDbId(), saved.id(), saved.versionNo(),
          saved.score().total().toPlainString(), saved.passed());
      if (committed.created()) {
        notifyReviewSaved(saved);
      }
      return PracticeReviewResult.saved(saved);
    } catch (RuntimeException exception) {
      log.warn(
          "Practice code review save failed. sessionId={} userMessageId={} agentRunDbId={} exceptionType={}",
          context.sessionId(), context.userMessageId(), context.agentRunDbId(), exception.getClass().getSimpleName(), exception);
      return PracticeReviewResult.failed(
          FAILURE_CODE_SAVE_FAILED,
          Map.of("failureCode", FAILURE_CODE_SAVE_FAILED));
    }
  }

  private void notifyReviewSaved(PracticeCodeReview saved) {
    try {
      observer.onReviewSaved(saved);
    } catch (RuntimeException exception) {
      log.warn(
          "Practice code review observer failed. reviewId={} userId={} sessionId={} exceptionType={}",
          saved.id(), saved.userId(), saved.sessionId(), exception.getClass().getSimpleName(), exception);
    }
  }

  private String usageSummary(LlmUsage usage) {
    return "input=%d,output=%d,cached=%d,reasoning=%d,total=%d".formatted(
        usage.inputTokens(), usage.outputTokens(), usage.cachedTokens(), usage.reasoningTokens(), usage.totalTokens());
  }

  private String structuredOutputSummary(JsonNode output) {
    if (output == null) {
      return "missing";
    }
    if (!output.isObject()) {
      return "type=%s".formatted(output.getNodeType());
    }
    return "isCodeSubmission=%s,belongsToCurrentProblem=%s,isCompleteLeetCodeSolution=%s,judgeVerdict=%s,hasScores=%s,rawCodeLength=%d,normalizedCodeLength=%d,fieldCount=%d"
        .formatted(
            booleanField(output, "isCodeSubmission"),
            booleanField(output, "belongsToCurrentProblem"),
            booleanField(output, "isCompleteLeetCodeSolution"),
            textField(output.path(PracticeCodeReviewConstants.JSON_JUDGE_ASSESSMENT), PracticeCodeReviewConstants.JSON_JUDGE_VERDICT),
            output.path("scores").isObject(),
            textLength(output, "rawCode"),
            textLength(output, "normalizedCode"),
            output.size());
  }

  private String booleanField(JsonNode output, String field) {
    JsonNode value = output.path(field);
    return value.isBoolean() ? Boolean.toString(value.booleanValue()) : "missing";
  }

  private int textLength(JsonNode output, String field) {
    JsonNode value = output.path(field);
    return value.isTextual() ? value.asText().length() : 0;
  }

  private String textField(JsonNode output, String field) {
    JsonNode value = output.path(field);
    return value.isTextual() ? value.asText() : "missing";
  }

  private String draftSummary(Optional<PracticeCodeReviewDraft> draft) {
    return draft
        .map(value -> "language=%s,totalScore=%s,passed=%s,rawCodeLength=%d,normalizedCodeLength=%d,evidenceCount=%d,deductionCount=%d,suggestionCount=%d"
            .formatted(
                value.language(), value.score().total().toPlainString(), value.passed(), value.rawCode().length(),
                value.normalizedCode().length(), value.evidence().size(), value.deductionReasons().size(),
                value.improvementSuggestions().size()))
        .orElse("absent");
  }

  private record RuntimeReviewOutput(
      JsonNode structuredOutput,
      String provider,
      String model,
      LlmUsage usage,
      String finishReason
  ) {
  }
}
