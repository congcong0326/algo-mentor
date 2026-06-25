package org.congcong.algomentor.mentor.application.practice;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;

public class PracticeCodeReviewService {

  public static final String FAILURE_CODE_LLM_COMPLETION_FAILED = "PRACTICE_CODE_REVIEW_LLM_FAILED";
  public static final String FAILURE_CODE_SAVE_FAILED = "PRACTICE_CODE_REVIEW_SAVE_FAILED";
  public static final String FAILURE_CODE_REPLAY_REVIEW_MISSING = "PRACTICE_CODE_REVIEW_REPLAY_MISSING";

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

    return repository.findByUserMessage(context.userId(), context.sessionId(), context.userMessageId())
        .map(PracticeReviewResult::saved)
        .orElseGet(() -> reviewWithLlm(context));
  }

  public PracticeReviewResult replay(PracticeTurnContext context) {
    Objects.requireNonNull(context, "context must not be null");
    if (delegate != null) {
      return delegate.apply(context);
    }

    return repository.findByUserMessage(context.userId(), context.sessionId(), context.userMessageId())
        .map(PracticeReviewResult::saved)
        .orElseGet(() -> PracticeReviewResult.failed(
            FAILURE_CODE_REPLAY_REVIEW_MISSING,
            Map.of("failureCode", FAILURE_CODE_REPLAY_REVIEW_MISSING)));
  }

  private PracticeReviewResult reviewWithLlm(PracticeTurnContext context) {
    LlmCompletionResult completion;
    try {
      completion = llmGateway.complete(request(context));
    } catch (RuntimeException exception) {
      return PracticeReviewResult.failed(
          FAILURE_CODE_LLM_COMPLETION_FAILED,
          Map.of("failureCode", FAILURE_CODE_LLM_COMPLETION_FAILED));
    }

    PracticeReviewResult mapped = outputMapper.map(context, completion.structuredOutput());
    if (mapped.status() != PracticeReviewStatus.REVIEWED) {
      return mapped;
    }

    try {
      PracticeCodeReview saved = repository.save(mapped.draft().orElseThrow());
      return PracticeReviewResult.saved(saved);
    } catch (RuntimeException exception) {
      return PracticeReviewResult.failed(
          FAILURE_CODE_SAVE_FAILED,
          Map.of("failureCode", FAILURE_CODE_SAVE_FAILED));
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
}
