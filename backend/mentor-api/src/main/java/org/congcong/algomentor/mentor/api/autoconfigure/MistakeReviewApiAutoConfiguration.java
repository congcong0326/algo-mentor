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
import org.congcong.algomentor.api.config.ReviewProperties;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.mentor.application.review.MicrometerMistakeReviewMetrics;
import org.congcong.algomentor.mentor.application.review.MistakeNoteRepository;
import org.congcong.algomentor.mentor.application.review.MistakeNoteService;
import org.congcong.algomentor.mentor.application.review.MistakeReviewMetrics;
import org.congcong.algomentor.mentor.application.review.PracticeCodeReviewObserver;
import org.congcong.algomentor.mentor.application.review.RecallJudgeService;
import org.congcong.algomentor.mentor.application.review.ReviewCardPregenerationService;
import org.congcong.algomentor.mentor.application.review.ReviewCardProperties;
import org.congcong.algomentor.mentor.application.review.ReviewCardService;
import org.congcong.algomentor.mentor.application.review.ReviewLogRepository;
import org.congcong.algomentor.mentor.application.review.ReviewSchedulerProperties;
import org.congcong.algomentor.mentor.application.review.ReviewSchedulerService;
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
        properties.getScheduler().getGraduationRepetitions());
  }

  @Bean
  @ConditionalOnMissingBean
  public RuleBasedCardComposer ruleBasedCardComposer() {
    return new RuleBasedCardComposer();
  }

  @Bean
  @ConditionalOnMissingBean
  public ReviewSchedulerService reviewSchedulerService(ReviewSchedulerProperties properties) {
    return new ReviewSchedulerService(properties);
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
      ReviewCardProperties properties
  ) {
    return new ReviewCardService(
        llmGateway,
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
      ObjectProvider<MistakeReviewMetrics> metrics
  ) {
    return new RecallJudgeService(
        llmGateway,
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
  @ConditionalOnBean({MistakeNoteRepository.class, ReviewCardPregenerationService.class, ObjectMapper.class})
  @ConditionalOnMissingBean
  public MistakeNoteService mistakeNoteService(
      MistakeNoteRepository repository,
      ReviewCardPregenerationService pregenerationService,
      ObjectMapper objectMapper,
      ObjectProvider<MistakeReviewMetrics> metrics
  ) {
    return new MistakeNoteService(
        repository,
        pregenerationService,
        objectMapper,
        metrics.getIfAvailable(() -> MistakeReviewMetrics.NOOP),
        Clock.systemUTC());
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
      ReviewSchedulerService.class,
      ReviewCardService.class,
      RecallJudgeService.class,
      ReviewCardPregenerationService.class,
      ObjectMapper.class
  })
  @ConditionalOnMissingBean
  public ReviewSessionService reviewSessionService(
      MistakeNoteRepository noteRepository,
      ReviewLogRepository logRepository,
      ReviewSchedulerService schedulerService,
      ReviewCardService cardService,
      RecallJudgeService judgeService,
      ReviewCardPregenerationService pregenerationService,
      ReviewCardProperties cardProperties,
      ObjectMapper objectMapper,
      ObjectProvider<MistakeReviewMetrics> metrics
  ) {
    return new ReviewSessionService(
        noteRepository,
        logRepository,
        schedulerService,
        cardService,
        judgeService,
        pregenerationService,
        cardProperties,
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
}
