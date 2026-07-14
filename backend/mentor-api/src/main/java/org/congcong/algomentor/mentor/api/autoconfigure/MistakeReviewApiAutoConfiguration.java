package org.congcong.algomentor.mentor.api.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.congcong.algomentor.ai.governance.completion.AiCompletionGateway;
import org.congcong.algomentor.ai.governance.completion.AiPassthroughCompletionGateway;
import org.congcong.algomentor.api.config.ReviewProperties;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.mentor.application.review.MicrometerMistakeReviewMetrics;
import org.congcong.algomentor.mentor.application.review.FsrsReviewSchedulerService;
import org.congcong.algomentor.mentor.application.review.MistakeNoteRepository;
import org.congcong.algomentor.mentor.application.review.MistakeNoteService;
import org.congcong.algomentor.mentor.application.review.MistakeReviewMetrics;
import org.congcong.algomentor.mentor.application.review.PracticeCodeReviewObserver;
import org.congcong.algomentor.mentor.application.review.RecallJudgeService;
import org.congcong.algomentor.mentor.application.review.ReviewPreferenceRepository;
import org.congcong.algomentor.mentor.application.review.ReviewPreferenceService;
import org.congcong.algomentor.mentor.application.review.ReviewRecallEvaluationRepository;
import org.congcong.algomentor.mentor.application.review.ReviewCardPregenerationService;
import org.congcong.algomentor.mentor.application.review.ReviewCardProperties;
import org.congcong.algomentor.mentor.application.review.ReviewCardService;
import org.congcong.algomentor.mentor.application.review.ReviewLogRepository;
import org.congcong.algomentor.mentor.application.review.ReviewProblemCatalog;
import org.congcong.algomentor.mentor.application.review.ReviewProblemSnapshot;
import org.congcong.algomentor.mentor.application.review.ReviewSchedulerProperties;
import org.congcong.algomentor.mentor.application.review.ReviewSeedPolicy;
import org.congcong.algomentor.mentor.application.review.ReviewSessionService;
import org.congcong.algomentor.mentor.application.review.RuleBasedCardComposer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AgentConversationApiAutoConfiguration.class)
@EnableConfigurationProperties(ReviewProperties.class)
public class MistakeReviewApiAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ReviewCardProperties reviewCardProperties(ReviewProperties properties) {
    return new ReviewCardProperties(
        properties.getCardGen().getDailyLimit(),
        Duration.ofHours(properties.getCardGen().getCacheTtlHours()),
        properties.getCardGen().getPrefetchCount());
  }

  @Bean
  @ConditionalOnMissingBean
  public ReviewSchedulerProperties reviewSchedulerProperties(ReviewProperties properties) {
    return new ReviewSchedulerProperties(
        properties.getScheduler().getGraduationIntervalDays(),
        properties.getScheduler().getGraduationRepetitions(),
        properties.getSeed().getPassedFirstIntervalDays(),
        properties.getSeed().getPassedHighScoreIntervalDays(),
        properties.getSeed().getLowConfidenceIntervalDays(),
        properties.getSeed().getHighScoreRatio(),
        properties.getQueue().getDailyCap(),
        properties.getScheduler().getDesiredRetention(),
        null,
        null,
        properties.getScheduler().getMaximumIntervalDays(),
        properties.getScheduler().isEnableFuzzing(),
        properties.getQueue().getDailyNewLimit(),
        properties.getQueue().getDailyLearningLimit(),
        properties.getQueue().getDailyReviewLimit());
  }

  @Bean
  @ConditionalOnMissingBean
  public ReviewSeedPolicy reviewSeedPolicy(ReviewSchedulerProperties properties) {
    return new ReviewSeedPolicy(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public RuleBasedCardComposer ruleBasedCardComposer() {
    return new RuleBasedCardComposer();
  }

  @Bean
  @ConditionalOnMissingBean
  public FsrsReviewSchedulerService reviewSchedulerService(ReviewSchedulerProperties properties) {
    return new FsrsReviewSchedulerService(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public ReviewPreferenceService reviewPreferenceService(
      ObjectProvider<ReviewPreferenceRepository> repository,
      ReviewSchedulerProperties properties
  ) {
    return new ReviewPreferenceService(
        repository.getIfAvailable(ReviewPreferenceRepository::empty),
        properties,
        Clock.systemUTC());
  }

  @Bean
  @ConditionalOnMissingBean
  public ReviewRecallEvaluationRepository reviewRecallEvaluationRepository() {
    return ReviewRecallEvaluationRepository.memory();
  }

  @Bean
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnMissingBean
  public MistakeReviewMetrics mistakeReviewMetrics(MeterRegistry registry) {
    return new MicrometerMistakeReviewMetrics(registry);
  }

  @Bean
  @ConditionalOnMissingBean(MistakeReviewMetrics.class)
  public MistakeReviewMetrics noopMistakeReviewMetrics() {
    return MistakeReviewMetrics.NOOP;
  }

  @Bean
  @ConditionalOnMissingBean(name = "reviewCardPregenerationExecutor")
  public Executor reviewCardPregenerationExecutor() {
    return Executors.newFixedThreadPool(1);
  }

  @Bean
  @ConditionalOnBean({LlmGateway.class, ObjectMapper.class, RuleBasedCardComposer.class})
  @ConditionalOnMissingBean
  public ReviewCardService reviewCardService(
      LlmGateway llmGateway,
      ObjectMapper objectMapper,
      RuleBasedCardComposer ruleBasedCardComposer,
      ReviewCardProperties properties,
      ObjectProvider<AiCompletionGateway> completionGateway
  ) {
    return new ReviewCardService(
        completionGateway.getIfAvailable(() -> new AiPassthroughCompletionGateway(llmGateway)),
        objectMapper,
        ruleBasedCardComposer,
        properties,
        Clock.systemUTC());
  }

  @Bean
  @ConditionalOnBean({LlmGateway.class, ObjectMapper.class})
  @ConditionalOnMissingBean
  public RecallJudgeService recallJudgeService(
      LlmGateway llmGateway,
      ObjectMapper objectMapper,
      ObjectProvider<MistakeReviewMetrics> metrics,
      ObjectProvider<AiCompletionGateway> completionGateway
  ) {
    return new RecallJudgeService(
        completionGateway.getIfAvailable(() -> new AiPassthroughCompletionGateway(llmGateway)),
        objectMapper,
        metrics.getIfAvailable(() -> MistakeReviewMetrics.NOOP));
  }

  @Bean
  @ConditionalOnBean({MistakeNoteRepository.class, ReviewCardService.class})
  @ConditionalOnMissingBean
  public ReviewCardPregenerationService reviewCardPregenerationService(
      @Qualifier("reviewCardPregenerationExecutor") Executor reviewCardPregenerationExecutor,
      ObjectProvider<AiDailyUsageStore> usageStore,
      MistakeNoteRepository repository,
      ReviewCardService cardService,
      ReviewCardProperties properties,
      ObjectProvider<MistakeReviewMetrics> metrics
  ) {
    return new ReviewCardPregenerationService(
        reviewCardPregenerationExecutor,
        usageStore.getIfAvailable(NoopAiDailyUsageStore::new),
        repository,
        cardService,
        properties,
        metrics.getIfAvailable(() -> MistakeReviewMetrics.NOOP),
        Clock.systemUTC());
  }

  @Bean
  @ConditionalOnBean({MistakeNoteRepository.class, ReviewCardPregenerationService.class, ReviewSeedPolicy.class,
      ObjectMapper.class})
  @ConditionalOnMissingBean
  public MistakeNoteService mistakeNoteService(
      MistakeNoteRepository repository,
      ReviewCardPregenerationService pregenerationService,
      ReviewSeedPolicy seedPolicy,
      ObjectMapper objectMapper,
      ObjectProvider<MistakeReviewMetrics> metrics,
      ObjectProvider<ReviewProblemCatalog> problemCatalog
  ) {
    return new MistakeNoteService(
        repository,
        pregenerationService,
        seedPolicy,
        objectMapper,
        metrics.getIfAvailable(() -> MistakeReviewMetrics.NOOP),
        problemCatalog.getIfAvailable(NoopReviewProblemCatalog::new),
        Clock.systemUTC());
  }

  @Bean
  @ConditionalOnMissingBean
  public ReviewProblemCatalog noopReviewProblemCatalog() {
    return new NoopReviewProblemCatalog();
  }

  @Bean
  @ConditionalOnBean(MistakeNoteService.class)
  @ConditionalOnMissingBean
  public PracticeCodeReviewObserver mistakePracticeCodeReviewObserver(MistakeNoteService mistakeNoteService) {
    return mistakeNoteService::ingestFromReview;
  }

  @Bean
  @ConditionalOnBean({
      MistakeNoteRepository.class,
      ReviewLogRepository.class,
      FsrsReviewSchedulerService.class,
      ReviewCardService.class,
      RecallJudgeService.class,
      ReviewPreferenceService.class,
      ReviewCardPregenerationService.class,
      ReviewSchedulerProperties.class,
      ObjectMapper.class
  })
  @ConditionalOnMissingBean
  public ReviewSessionService reviewSessionService(
      MistakeNoteRepository noteRepository,
      ReviewLogRepository logRepository,
      FsrsReviewSchedulerService schedulerService,
      ReviewCardService cardService,
      RecallJudgeService judgeService,
      ReviewRecallEvaluationRepository evaluationRepository,
      ReviewPreferenceService preferenceService,
      ReviewCardPregenerationService pregenerationService,
      ReviewCardProperties cardProperties,
      ReviewSchedulerProperties schedulerProperties,
      ObjectMapper objectMapper,
      ObjectProvider<MistakeReviewMetrics> metrics
  ) {
    return new ReviewSessionService(
        noteRepository,
        logRepository,
        schedulerService,
        cardService,
        judgeService,
        evaluationRepository,
        preferenceService,
        pregenerationService,
        cardProperties,
        schedulerProperties,
        objectMapper,
        metrics.getIfAvailable(() -> MistakeReviewMetrics.NOOP),
        Clock.systemUTC());
  }

  private static final class NoopAiDailyUsageStore implements AiDailyUsageStore {
    @Override
    public boolean tryConsumeRequest(long userId, LocalDate quotaDate, String scope, long limitCount) {
      return false;
    }

    @Override
    public void addUsage(long userId, LocalDate quotaDate, String scope, AiUsage usage) {
    }
  }

  private static final class NoopReviewProblemCatalog implements ReviewProblemCatalog {
    @Override
    public java.util.Optional<ReviewProblemSnapshot> findBySlug(String slug) {
      return java.util.Optional.empty();
    }
  }
}
