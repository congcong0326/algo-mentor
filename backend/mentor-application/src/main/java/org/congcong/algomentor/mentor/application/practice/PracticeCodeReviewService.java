package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.congcong.algomentor.ai.governance.completion.AiCompletionContext;
import org.congcong.algomentor.ai.governance.completion.AiCompletionGateway;
import org.congcong.algomentor.ai.governance.completion.AiPassthroughCompletionGateway;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.mentor.application.review.PracticeCodeReviewObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PracticeCodeReviewService {

  public static final String FAILURE_CODE_LLM_COMPLETION_FAILED = "PRACTICE_CODE_REVIEW_LLM_FAILED";
  public static final String FAILURE_CODE_SAVE_FAILED = "PRACTICE_CODE_REVIEW_SAVE_FAILED";
  public static final String FAILURE_CODE_REPLAY_REVIEW_MISSING = "PRACTICE_CODE_REVIEW_REPLAY_MISSING";

  private static final Logger log = LoggerFactory.getLogger(PracticeCodeReviewService.class);

  private final PracticeCodeReviewRepository repository;
  private final PracticeCodeReviewCommitService commitService;
  private final AiCompletionGateway completionGateway;
  private final PracticeCodeReviewPromptBuilder promptBuilder;
  private final PracticeCodeReviewStructuredOutputMapper outputMapper;
  private final PracticeCodeReviewMetrics metrics;
  private final PracticeCodeReviewObserver observer;
  private final Function<PracticeTurnContext, PracticeReviewResult> delegate;

  public PracticeCodeReviewService(
      PracticeCodeReviewRepository repository,
      PracticeCodeReviewCommitService commitService,
      LlmGateway llmGateway,
      PracticeCodeReviewPromptBuilder promptBuilder,
      PracticeCodeReviewStructuredOutputMapper outputMapper
  ) {
    this(repository, commitService, llmGateway, promptBuilder, outputMapper, PracticeCodeReviewMetrics.NOOP);
  }

  public PracticeCodeReviewService(
      PracticeCodeReviewRepository repository,
      PracticeCodeReviewCommitService commitService,
      LlmGateway llmGateway,
      PracticeCodeReviewPromptBuilder promptBuilder,
      PracticeCodeReviewStructuredOutputMapper outputMapper,
      PracticeCodeReviewMetrics metrics
  ) {
    this(repository, commitService, llmGateway, promptBuilder, outputMapper, metrics, PracticeCodeReviewObserver.NOOP);
  }

  public PracticeCodeReviewService(
      PracticeCodeReviewRepository repository,
      PracticeCodeReviewCommitService commitService,
      LlmGateway llmGateway,
      PracticeCodeReviewPromptBuilder promptBuilder,
      PracticeCodeReviewStructuredOutputMapper outputMapper,
      PracticeCodeReviewMetrics metrics,
      PracticeCodeReviewObserver observer
  ) {
    this(
        repository,
        commitService,
        new AiPassthroughCompletionGateway(llmGateway),
        promptBuilder,
        outputMapper,
        metrics,
        observer);
  }

  public PracticeCodeReviewService(
      PracticeCodeReviewRepository repository,
      PracticeCodeReviewCommitService commitService,
      AiCompletionGateway completionGateway,
      PracticeCodeReviewPromptBuilder promptBuilder,
      PracticeCodeReviewStructuredOutputMapper outputMapper,
      PracticeCodeReviewMetrics metrics,
      PracticeCodeReviewObserver observer
  ) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.commitService = Objects.requireNonNull(commitService, "commitService must not be null");
    this.completionGateway = Objects.requireNonNull(completionGateway, "completionGateway must not be null");
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder must not be null");
    this.outputMapper = Objects.requireNonNull(outputMapper, "outputMapper must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.observer = Objects.requireNonNull(observer, "observer must not be null");
    this.delegate = null;
  }

  protected PracticeCodeReviewService(Function<PracticeTurnContext, PracticeReviewResult> delegate) {
    this.repository = null;
    this.commitService = null;
    this.completionGateway = null;
    this.promptBuilder = null;
    this.outputMapper = null;
    this.metrics = PracticeCodeReviewMetrics.NOOP;
    this.observer = PracticeCodeReviewObserver.NOOP;
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
  }

  public PracticeReviewResult review(PracticeTurnContext context) {
    return review(context, defaultCompletionContext(context));
  }

  public PracticeReviewResult review(PracticeTurnContext context, AiCompletionContext completionContext) {
    Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(completionContext, "completionContext must not be null");
    if (delegate != null) {
      return delegate.apply(context);
    }

    PracticeReviewResult result;
    Optional<PracticeCodeReview> existing = repository.findByUserMessage(
        context.userId(),
        context.sessionId(),
        context.userMessageId());
    if (existing.isPresent()) {
      PracticeCodeReview review = existing.get();
      log.info(
          "Practice code review reused existing review. sessionId={} userMessageId={} reviewId={} versionNo={} agentRunDbId={}",
          context.sessionId(),
          context.userMessageId(),
          review.id(),
          review.versionNo(),
          review.agentRunDbId());
      result = PracticeReviewResult.saved(review);
      recordReviewResult(result);
      return result;
    }
    log.info(
        "Practice code review existing lookup missed. sessionId={} userMessageId={} agentRunDbId={} problemSlug={}",
        context.sessionId(),
        context.userMessageId(),
        context.agentRunDbId(),
        context.problemSlug());
    result = reviewWithLlm(context, completionContext);
    recordReviewResult(result);
    return result;
  }

  public PracticeReviewResult replay(PracticeTurnContext context) {
    Objects.requireNonNull(context, "context must not be null");
    if (delegate != null) {
      return delegate.apply(context);
    }

    PracticeReviewResult result;
    Optional<PracticeCodeReview> existing = repository.findByUserMessage(
        context.userId(),
        context.sessionId(),
        context.userMessageId());
    if (existing.isPresent()) {
      PracticeCodeReview review = existing.get();
      log.info(
          "Practice code review replay reused existing review. sessionId={} userMessageId={} reviewId={} versionNo={} agentRunDbId={}",
          context.sessionId(),
          context.userMessageId(),
          review.id(),
          review.versionNo(),
          review.agentRunDbId());
      result = PracticeReviewResult.saved(review);
      recordReviewResult(result);
      return result;
    }
    log.warn(
        "Practice code review replay missing existing review. sessionId={} userMessageId={} agentRunDbId={} problemSlug={}",
        context.sessionId(),
        context.userMessageId(),
        context.agentRunDbId(),
        context.problemSlug());
    result = PracticeReviewResult.failed(
        FAILURE_CODE_REPLAY_REVIEW_MISSING,
        Map.of("failureCode", FAILURE_CODE_REPLAY_REVIEW_MISSING));
    recordReviewResult(result);
    return result;
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

  private PracticeReviewResult reviewWithLlm(
      PracticeTurnContext context,
      AiCompletionContext completionContext
  ) {
    LlmCompletionResult completion;
    LlmCompletionRequest request = request(context);
    try {
      log.info(
          "Practice code review LLM request started. sessionId={} userMessageId={} agentRunDbId={} problemSlug={} codeProvided={} codeLength={} promptMessageCount={} responseSchema={}",
          context.sessionId(),
          context.userMessageId(),
          context.agentRunDbId(),
          context.problemSlug(),
          !context.extractedCode().isBlank(),
          context.extractedCode().length(),
          request.messages().size(),
          PracticeCodeReviewConstants.SCHEMA_NAME);
      completion = completionGateway.complete(request, completionContext);
    } catch (RuntimeException exception) {
      if (exception instanceof LlmException llmException) {
        log.warn(
            "Practice code review LLM request failed. sessionId={} userMessageId={} agentRunDbId={} exceptionType={} code={} retryable={} provider={} model={} metadata={} causeType={} causeMessage={}",
            context.sessionId(),
            context.userMessageId(),
            context.agentRunDbId(),
            exception.getClass().getSimpleName(),
            llmException.code(),
            llmException.retryable(),
            llmException.provider() == null ? "" : llmException.provider().value(),
            llmException.model() == null ? "" : llmException.model().value(),
            llmException.metadata(),
            causeType(llmException),
            causeMessage(llmException),
            exception);
      } else {
        log.warn(
            "Practice code review LLM request failed. sessionId={} userMessageId={} agentRunDbId={} exceptionType={}",
            context.sessionId(),
            context.userMessageId(),
            context.agentRunDbId(),
            exception.getClass().getSimpleName(),
            exception);
      }
      return PracticeReviewResult.failed(
          FAILURE_CODE_LLM_COMPLETION_FAILED,
          Map.of("failureCode", FAILURE_CODE_LLM_COMPLETION_FAILED));
    }

    log.info(
        "Practice code review LLM request completed. sessionId={} userMessageId={} agentRunDbId={} provider={} model={} finishReason={} usage={} structuredOutput={}",
        context.sessionId(),
        context.userMessageId(),
        context.agentRunDbId(),
        completion.provider().value(),
        completion.model().value(),
        completion.finishReason(),
        usageSummary(completion.usage()),
        structuredOutputSummary(completion.structuredOutput()));

    PracticeReviewResult mapped = outputMapper.map(context, completion.structuredOutput());
    log.info(
        "Practice code review structured output mapped. sessionId={} userMessageId={} agentRunDbId={} status={} failureCode={} draft={}",
        context.sessionId(),
        context.userMessageId(),
        context.agentRunDbId(),
        mapped.status(),
        mapped.failureCode(),
        draftSummary(mapped.draft()));
    if (mapped.status() != PracticeReviewStatus.REVIEWED) {
      log.warn(
          "Practice code review structured output was not reviewable. sessionId={} userMessageId={} agentRunDbId={} status={} failureCode={}",
          context.sessionId(),
          context.userMessageId(),
          context.agentRunDbId(),
          mapped.status(),
          mapped.failureCode());
      return mapped;
    }

    return saveReviewedDraft(context, mapped.draft().orElseThrow());
  }

  private PracticeReviewResult saveReviewedDraft(PracticeTurnContext context, PracticeCodeReviewDraft draft) {
    try {
      log.info(
          "Practice code review save started. sessionId={} userMessageId={} agentRunDbId={} problemSlug={} language={} rawCodeLength={} normalizedCodeLength={} evidenceCount={} totalScore={} passed={}",
          context.sessionId(),
          context.userMessageId(),
          context.agentRunDbId(),
          draft.problemSlug(),
          draft.language(),
          draft.rawCode().length(),
          draft.normalizedCode().length(),
          draft.evidence().size(),
          draft.score().total().toPlainString(),
          draft.passed());
      PracticeCodeReviewCommitResult committed = commitService.commit(draft);
      PracticeCodeReview saved = committed.review();
      log.info(
          "Practice code review saved. sessionId={} userMessageId={} agentRunDbId={} reviewId={} versionNo={} totalScore={} passed={}",
          context.sessionId(),
          context.userMessageId(),
          context.agentRunDbId(),
          saved.id(),
          saved.versionNo(),
          saved.score().total().toPlainString(),
          saved.passed());
      if (committed.created()) {
        notifyReviewSaved(saved);
      }
      return PracticeReviewResult.saved(saved);
    } catch (RuntimeException exception) {
      log.warn(
          "Practice code review save failed. sessionId={} userMessageId={} agentRunDbId={} exceptionType={}",
          context.sessionId(),
          context.userMessageId(),
          context.agentRunDbId(),
          exception.getClass().getSimpleName(),
          exception);
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
          saved.id(),
          saved.userId(),
          saved.sessionId(),
          exception.getClass().getSimpleName(),
          exception);
    }
  }

  private LlmCompletionRequest request(PracticeTurnContext context) {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.requiring(Set.of(LlmCapability.JSON_SCHEMA_OUTPUT)))
        .messages(promptBuilder.build(context))
        .responseFormat(new LlmResponseFormat.JsonSchema(
            PracticeCodeReviewConstants.SCHEMA_NAME,
            PracticeCodeReviewJsonSchema.schema(),
            true))
        .metadata(Map.of(
            PracticeCodeReviewConstants.METADATA_REVIEW_CANDIDATE, true,
            PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, context.sessionId()))
        .build();
  }

  private AiCompletionContext defaultCompletionContext(PracticeTurnContext context) {
    String runId = context.agentRunDbId() == null ? null : "practice-review-" + context.agentRunDbId();
    return AiCompletionContext.parentRun(
        context.userId(),
        runId,
        AiPurpose.LEARNING_CHAT,
        AiRunSource.PRACTICE_CODE_REVIEW,
        1);
  }

  private String usageSummary(LlmUsage usage) {
    return "input=%d,output=%d,cached=%d,reasoning=%d,total=%d".formatted(
        usage.inputTokens(),
        usage.outputTokens(),
        usage.cachedTokens(),
        usage.reasoningTokens(),
        usage.totalTokens());
  }

  private String structuredOutputSummary(JsonNode output) {
    if (output == null) {
      return "missing";
    }
    if (!output.isObject()) {
      return "type=%s".formatted(output.getNodeType());
    }
    return "isCodeSubmission=%s,belongsToCurrentProblem=%s,isCompleteLeetCodeSolution=%s,hasScores=%s,rawCodeLength=%d,normalizedCodeLength=%d,fieldCount=%d"
        .formatted(
            booleanField(output, "isCodeSubmission"),
            booleanField(output, "belongsToCurrentProblem"),
            booleanField(output, "isCompleteLeetCodeSolution"),
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

  private String draftSummary(Optional<PracticeCodeReviewDraft> draft) {
    return draft
        .map(value -> "language=%s,totalScore=%s,passed=%s,rawCodeLength=%d,normalizedCodeLength=%d,evidenceCount=%d,deductionCount=%d,suggestionCount=%d"
            .formatted(
                value.language(),
                value.score().total().toPlainString(),
                value.passed(),
                value.rawCode().length(),
                value.normalizedCode().length(),
                value.evidence().size(),
                value.deductionReasons().size(),
                value.improvementSuggestions().size()))
        .orElse("absent");
  }

  private String causeType(Throwable error) {
    Throwable cause = error.getCause();
    return cause == null ? "none" : cause.getClass().getName();
  }

  private String causeMessage(Throwable error) {
    Throwable cause = error.getCause();
    if (cause == null || cause.getMessage() == null || cause.getMessage().isBlank()) {
      return "";
    }
    return cause.getMessage();
  }
}
