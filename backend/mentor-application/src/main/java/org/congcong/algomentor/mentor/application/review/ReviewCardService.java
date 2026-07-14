package org.congcong.algomentor.mentor.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReviewCardService {

  private static final Logger log = LoggerFactory.getLogger(ReviewCardService.class);

  private final AiCompletionGateway completionGateway;
  private final ObjectMapper objectMapper;
  private final RuleBasedCardComposer ruleBasedCardComposer;
  private final ReviewCardProperties properties;
  private final Clock clock;
  private ReviewCardPregenerationService pregenerationService;

  public ReviewCardService(
      LlmGateway llmGateway,
      ObjectMapper objectMapper,
      RuleBasedCardComposer ruleBasedCardComposer,
      ReviewCardProperties properties,
      Clock clock
  ) {
    this(new AiPassthroughCompletionGateway(llmGateway), objectMapper, ruleBasedCardComposer, properties, clock);
  }

  public ReviewCardService(
      AiCompletionGateway completionGateway,
      ObjectMapper objectMapper,
      RuleBasedCardComposer ruleBasedCardComposer,
      ReviewCardProperties properties,
      Clock clock
  ) {
    this.completionGateway = Objects.requireNonNull(completionGateway, "completionGateway must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.ruleBasedCardComposer = Objects.requireNonNull(ruleBasedCardComposer, "ruleBasedCardComposer must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public void setPregenerationService(ReviewCardPregenerationService pregenerationService) {
    this.pregenerationService = pregenerationService;
  }

  public ReviewCard getOrFallback(MistakeNote note) {
    String signature = signature(note);
    ReviewCardCache cache = note.pendingCard();
    if (cacheHit(note, signature)) {
      return withNoteProblemContext(note, objectMapper.convertValue(cache.cardJson(), ReviewCard.class));
    }
    if (pregenerationService != null) {
      pregenerationService.enqueue(note.id());
    }
    return ruleBasedCardComposer.compose(note);
  }

  public ReviewCard generate(MistakeNote note) {
    LlmCompletionRequest request = request(note);
    try {
      log.info("Review card generation LLM request started. noteId={} userId={} problemSlug={} responseSchema={}",
          note.id(), note.userId(), note.problemSlug(), MistakeReviewConstants.CARD_SCHEMA_NAME);
      LlmCompletionResult result = completionGateway.complete(request, backgroundContext(note));
      ReviewCard card = objectMapper.convertValue(result.structuredOutput(), ReviewCard.class);
      return new ReviewCard(
          CardVariant.AI_GENERATED,
          problemRef(note, card.problemRef()),
          problemStatement(note),
          card.contextSummary(),
          card.prompts(),
          card.scaffold(),
          card.revealPolicy(),
          card.expectedEffort());
    } catch (RuntimeException exception) {
      if (exception instanceof LlmException llmException) {
        log.warn(
            "Review card generation LLM failed. noteId={} userId={} problemSlug={} code={} retryable={} provider={} model={}",
            note.id(),
            note.userId(),
            note.problemSlug(),
            llmException.code(),
            llmException.retryable(),
            llmException.provider() == null ? "" : llmException.provider().value(),
            llmException.model() == null ? "" : llmException.model().value(),
            exception);
      } else {
        log.warn("Review card generation failed. noteId={} userId={} problemSlug={} exceptionType={}",
            note.id(), note.userId(), note.problemSlug(), exception.getClass().getSimpleName(), exception);
      }
      return null;
    }
  }

  public boolean cacheHit(MistakeNote note, String signature) {
    ReviewCardCache cache = note.pendingCard();
    if (cache == null || cache.variant() != CardVariant.AI_GENERATED || cache.generatedAt() == null) {
      return false;
    }
    if (!Objects.equals(signature, cache.signature())) {
      return false;
    }
    Instant expiresAt = cache.generatedAt().plus(properties.cacheTtl());
    return expiresAt.isAfter(Instant.now(clock));
  }

  public boolean canGenerateAi(MistakeNote note) {
    return completionGateway.isAllowed(backgroundContext(note));
  }

  public String signature(MistakeNote note) {
    String raw = "%s:%s:%s:%s:%s".formatted(
        note.problemSlug(),
        note.sourceDetail().getOrDefault("latestReviewId", ""),
        note.sourceDetail().getOrDefault("latestReviewScore", ""),
        note.scheduling().fsrsState(),
        note.lastRating() == null ? "" : note.lastRating().name(),
        note.scheduling().lapses());
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is not available", exception);
    }
  }

  public JsonNode toJson(ReviewCard card) {
    return objectMapper.valueToTree(card);
  }

  private LlmCompletionRequest request(MistakeNote note) {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.requiring(Set.of(LlmCapability.JSON_SCHEMA_OUTPUT)))
        .messages(java.util.List.of(
            LlmMessage.system("""
                你是 algo-mentor 的错题复习卡生成器。只生成结构化 JSON。
                卡片必须遵守：不泄露完整题解或旧代码；围绕算法选择、关键步骤、复杂度和边界追问；让用户少写多想。
                cardVariant 固定为 AI_GENERATED，revealPolicy 固定为 HIDE_PREVIOUS_CODE_AND_SOLUTION。
                """.stripIndent()),
            LlmMessage.user("""
                题目 slug：%s
                题目标题：%s
                难度：%s
                题眼摘要：%s
                FSRS 状态：%s
                lapses：%d
                上次复习评级：%s
                上次 Review 分数：%s
                上次扣分点：%s
                请生成 1-4 个针对薄弱点的复习问题，并给出不超过 400 字输入上限的 scaffold。
                """.stripIndent().formatted(
                note.problemSlug(),
                note.sourceDetail().getOrDefault(MistakeReviewConstants.METADATA_TITLE_CN, note.problemSlug()),
                note.sourceDetail().getOrDefault(MistakeReviewConstants.METADATA_DIFFICULTY, "UNKNOWN"),
                note.sourceDetail().getOrDefault(MistakeReviewConstants.METADATA_STATEMENT_SUMMARY, "未记录"),
                note.scheduling().fsrsState(),
                note.scheduling().lapses(),
                note.lastRating() == null ? "未知" : note.lastRating().name(),
                note.sourceDetail().getOrDefault("latestReviewScore", "未知"),
                note.sourceDetail().getOrDefault("deductionReasons", "未记录")))))
        .responseFormat(new LlmResponseFormat.JsonSchema(
            MistakeReviewConstants.CARD_SCHEMA_NAME,
            ReviewCardJsonSchema.schema(),
            true))
        .metadata(Map.of(MistakeReviewConstants.METADATA_MISTAKE_NOTE_ID, note.id()))
        .build();
  }

  private AiCompletionContext backgroundContext(MistakeNote note) {
    return AiCompletionContext.background(
        note.userId(),
        AiPurpose.PROBLEM_EXPLANATION,
        AiRunSource.REVIEW_CARD_GENERATION,
        MistakeReviewConstants.QUOTA_SCOPE,
        note.problemSlug().length());
  }

  private ReviewCard withNoteProblemContext(MistakeNote note, ReviewCard card) {
    if (card == null) {
      return null;
    }
    return new ReviewCard(
        card.cardVariant(),
        problemRef(note, card.problemRef()),
        problemStatement(note),
        card.contextSummary(),
        card.prompts(),
        card.scaffold(),
        card.revealPolicy(),
        card.expectedEffort());
  }

  private ProblemRef problemRef(MistakeNote note, ProblemRef fallback) {
    String titleCn = text(note, MistakeReviewConstants.METADATA_TITLE_CN,
        fallback == null ? note.problemSlug() : fallback.titleCn());
    String difficulty = text(note, MistakeReviewConstants.METADATA_DIFFICULTY,
        fallback == null ? "UNKNOWN" : fallback.difficulty());
    return new ProblemRef(note.problemSlug(), titleCn, difficulty);
  }

  private ProblemStatement problemStatement(MistakeNote note) {
    String summary = text(note, MistakeReviewConstants.METADATA_STATEMENT_SUMMARY, "");
    if (summary.isBlank()) {
      return null;
    }
    return new ProblemStatement(summary, true);
  }

  private String text(MistakeNote note, String key, String fallback) {
    Object value = note.sourceDetail().get(key);
    if (value == null || value.toString().isBlank()) {
      return fallback;
    }
    return value.toString();
  }
}
