package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PracticeCodeReviewService {

  public static final String FAILURE_CODE_LLM_COMPLETION_FAILED = "PRACTICE_CODE_REVIEW_LLM_FAILED";
  public static final String FAILURE_CODE_SAVE_FAILED = "PRACTICE_CODE_REVIEW_SAVE_FAILED";
  public static final String FAILURE_CODE_REPLAY_REVIEW_MISSING = "PRACTICE_CODE_REVIEW_REPLAY_MISSING";

  private static final Logger log = LoggerFactory.getLogger(PracticeCodeReviewService.class);

  private final PracticeCodeReviewRepository repository;
  private final LlmGateway llmGateway;
  private final PracticeCodeReviewPromptBuilder promptBuilder;
  private final PracticeCodeReviewStructuredOutputMapper outputMapper;
  private final Function<PracticeTurnContext, PracticeReviewResult> delegate;

  public PracticeCodeReviewService(
      PracticeCodeReviewRepository repository,
      LlmGateway llmGateway,
      PracticeCodeReviewPromptBuilder promptBuilder,
      PracticeCodeReviewStructuredOutputMapper outputMapper
  ) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway must not be null");
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder must not be null");
    this.outputMapper = Objects.requireNonNull(outputMapper, "outputMapper must not be null");
    this.delegate = null;
  }

  protected PracticeCodeReviewService(Function<PracticeTurnContext, PracticeReviewResult> delegate) {
    this.repository = null;
    this.llmGateway = null;
    this.promptBuilder = null;
    this.outputMapper = null;
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
  }

  public PracticeReviewResult review(PracticeTurnContext context) {
    Objects.requireNonNull(context, "context must not be null");
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
          context.sessionId(),
          context.userMessageId(),
          review.id(),
          review.versionNo(),
          review.agentRunDbId());
      return PracticeReviewResult.saved(review);
    }
    log.info(
        "Practice code review existing lookup missed. sessionId={} userMessageId={} agentRunDbId={} problemSlug={}",
        context.sessionId(),
        context.userMessageId(),
        context.agentRunDbId(),
        context.problemSlug());
    return reviewWithLlm(context);
  }

  public PracticeReviewResult replay(PracticeTurnContext context) {
    Objects.requireNonNull(context, "context must not be null");
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
          "Practice code review replay reused existing review. sessionId={} userMessageId={} reviewId={} versionNo={} agentRunDbId={}",
          context.sessionId(),
          context.userMessageId(),
          review.id(),
          review.versionNo(),
          review.agentRunDbId());
      return PracticeReviewResult.saved(review);
    }
    log.warn(
        "Practice code review replay missing existing review. sessionId={} userMessageId={} agentRunDbId={} problemSlug={}",
        context.sessionId(),
        context.userMessageId(),
        context.agentRunDbId(),
        context.problemSlug());
    return PracticeReviewResult.failed(
        FAILURE_CODE_REPLAY_REVIEW_MISSING,
        Map.of("failureCode", FAILURE_CODE_REPLAY_REVIEW_MISSING));
  }

  private PracticeReviewResult reviewWithLlm(PracticeTurnContext context) {
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
      completion = llmGateway.complete(request);
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
      return saveRejectedAttempt(context, PracticeReviewResult.failed(
          FAILURE_CODE_LLM_COMPLETION_FAILED,
          Map.of("failureCode", FAILURE_CODE_LLM_COMPLETION_FAILED)));
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
      return saveRejectedAttempt(context, mapped);
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
      PracticeCodeReview saved = repository.save(draft);
      log.info(
          "Practice code review saved. sessionId={} userMessageId={} agentRunDbId={} reviewId={} versionNo={} totalScore={} passed={}",
          context.sessionId(),
          context.userMessageId(),
          context.agentRunDbId(),
          saved.id(),
          saved.versionNo(),
          saved.score().total().toPlainString(),
          saved.passed());
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

  private PracticeReviewResult saveRejectedAttempt(PracticeTurnContext context, PracticeReviewResult mapped) {
    log.info(
        "Practice code review rejected attempt will be persisted. sessionId={} userMessageId={} agentRunDbId={} mappedStatus={} failureCode={}",
        context.sessionId(),
        context.userMessageId(),
        context.agentRunDbId(),
        mapped.status(),
        mapped.failureCode());
    PracticeCodeReviewDraft draft = rejectedAttemptDraft(context, mapped);
    PracticeReviewResult saved = saveReviewedDraft(context, draft);
    if (saved.status() != PracticeReviewStatus.SAVED) {
      return saved;
    }
    Map<String, Object> metadata = new java.util.LinkedHashMap<>(saved.metadata());
    metadata.put("reviewAttemptStatus", mapped.status().name());
    if (mapped.failureCode() != null) {
      metadata.put("reviewAttemptFailureCode", mapped.failureCode());
    }
    return new PracticeReviewResult(saved.status(), saved.draft(), null, metadata);
  }

  private PracticeCodeReviewDraft rejectedAttemptDraft(PracticeTurnContext context, PracticeReviewResult mapped) {
    String reason = switch (mapped.status()) {
      case NOT_COMPLETE_SUBMISSION -> "代码提交分析已触发，但模型判断本轮内容不是当前题目的完整 LeetCode 提交。";
      case NOT_CODE_LIKE -> "代码提交分析已触发，但模型判断本轮内容不像代码提交。";
      case FAILED -> "代码提交分析已触发，但结构化结果无效，未能完成有效评分。";
      default -> "代码提交分析已触发，但未形成可通过的有效代码提交记录。";
    };
    if (mapped.failureCode() != null) {
      reason = reason + " failureCode=" + mapped.failureCode();
    }
    return new PracticeCodeReviewDraft(
        context.userId(),
        context.planId(),
        context.phaseIndex(),
        context.problemSlug(),
        context.sessionId(),
        context.userMessageId(),
        context.assistantMessageId(),
        context.agentRunDbId(),
        fallbackCode(context),
        fallbackCode(context),
        "unknown",
        List.of(new PracticeCodeReviewEvidence("REVIEW_ATTEMPT_REJECTED", mapped.status().name())),
        "代码候选已进入代码提交分析，但未形成有效评分。",
        zeroScore(),
        false,
        List.of(reason),
        List.of("请确认粘贴的是当前题目的完整 LeetCode 解法，并包含 class Solution 与入口方法。"),
        reason);
  }

  private String fallbackCode(PracticeTurnContext context) {
    if (!context.extractedCode().isBlank()) {
      return context.extractedCode();
    }
    return context.originalMessage().isBlank() ? "unavailable review candidate" : context.originalMessage();
  }

  private PracticeCodeReviewScore zeroScore() {
    return new PracticeCodeReviewScore(
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
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
