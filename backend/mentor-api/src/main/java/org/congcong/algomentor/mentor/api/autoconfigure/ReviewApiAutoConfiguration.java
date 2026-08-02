package org.congcong.algomentor.mentor.api.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.congcong.algomentor.api.config.ReviewProperties;
import org.congcong.algomentor.mentor.application.review.attempt.ReviewAttemptRepository;
import org.congcong.algomentor.mentor.application.review.attempt.ReviewAttemptService;
import org.congcong.algomentor.mentor.application.review.card.MicrometerReviewMetrics;
import org.congcong.algomentor.mentor.application.review.card.PracticeCodeReviewObserver;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardRepository;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardService;
import org.congcong.algomentor.mentor.application.review.card.ReviewMetrics;
import org.congcong.algomentor.mentor.application.review.card.ReviewQueueService;
import org.congcong.algomentor.mentor.application.review.catalog.ReviewProblemCatalog;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteRepository;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteService;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreferenceRepository;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreferenceService;
import org.congcong.algomentor.mentor.application.review.schedule.FsrsReviewSchedulerService;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSchedulerProperties;
import org.congcong.algomentor.mentor.application.review.schedule.ReviewSeedPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AgentConversationApiAutoConfiguration.class)
@EnableConfigurationProperties(ReviewProperties.class)
public class ReviewApiAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ReviewSchedulerProperties reviewSchedulerProperties(ReviewProperties properties) {
    return new ReviewSchedulerProperties(
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
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnMissingBean
  public ReviewMetrics reviewMetrics(MeterRegistry registry) {
    return new MicrometerReviewMetrics(registry);
  }

  @Bean
  @ConditionalOnMissingBean(ReviewMetrics.class)
  public ReviewMetrics noopReviewMetrics() {
    return ReviewMetrics.NOOP;
  }

  @Bean
  @ConditionalOnMissingBean
  public ReviewProblemCatalog noopReviewProblemCatalog() {
    return (slug, locale) -> java.util.Optional.empty();
  }

  @Bean
  @ConditionalOnBean({ReviewCardRepository.class, ReviewSeedPolicy.class, ObjectMapper.class})
  @ConditionalOnMissingBean
  public ReviewCardService reviewCardService(
      ReviewCardRepository repository,
      ReviewSeedPolicy seedPolicy,
      ObjectMapper objectMapper,
      ObjectProvider<ReviewMetrics> metrics,
      ReviewProblemCatalog problemCatalog
  ) {
    return new ReviewCardService(
        repository,
        seedPolicy,
        objectMapper,
        metrics.getIfAvailable(() -> ReviewMetrics.NOOP),
        problemCatalog,
        Clock.systemUTC());
  }

  @Bean
  @ConditionalOnBean(ReviewCardService.class)
  @ConditionalOnMissingBean
  public PracticeCodeReviewObserver reviewCardPracticeCodeReviewObserver(ReviewCardService reviewCardService) {
    return reviewCardService::ingestFromReview;
  }

  @Bean
  @ConditionalOnBean({UserProblemNoteRepository.class, ReviewProblemCatalog.class})
  @ConditionalOnMissingBean
  public UserProblemNoteService userProblemNoteService(
      UserProblemNoteRepository repository,
      ReviewProblemCatalog problemCatalog
  ) {
    return new UserProblemNoteService(repository, problemCatalog, Clock.systemUTC());
  }

  @Bean
  @ConditionalOnBean({
      ReviewCardRepository.class,
      ReviewAttemptRepository.class,
      FsrsReviewSchedulerService.class,
      ReviewPreferenceService.class
  })
  @ConditionalOnMissingBean
  public ReviewAttemptService reviewAttemptService(
      ReviewCardRepository cardRepository,
      ReviewAttemptRepository attemptRepository,
      FsrsReviewSchedulerService schedulerService,
      ReviewPreferenceService preferenceService,
      ObjectProvider<ReviewMetrics> metrics
  ) {
    return new ReviewAttemptService(
        cardRepository,
        attemptRepository,
        schedulerService,
        preferenceService,
        metrics.getIfAvailable(() -> ReviewMetrics.NOOP),
        Clock.systemUTC());
  }

  @Bean
  @ConditionalOnBean({
      ReviewCardRepository.class,
      ReviewAttemptRepository.class,
      UserProblemNoteRepository.class,
      ReviewProblemCatalog.class,
      FsrsReviewSchedulerService.class,
      ReviewPreferenceService.class
  })
  @ConditionalOnMissingBean
  public ReviewQueueService reviewQueueService(
      ReviewCardRepository cardRepository,
      ReviewAttemptRepository attemptRepository,
      UserProblemNoteRepository noteRepository,
      ReviewProblemCatalog problemCatalog,
      FsrsReviewSchedulerService schedulerService,
      ReviewPreferenceService preferenceService,
      ReviewSchedulerProperties schedulerProperties
  ) {
    return new ReviewQueueService(
        cardRepository,
        attemptRepository,
        noteRepository,
        problemCatalog,
        schedulerService,
        preferenceService,
        schedulerProperties,
        Clock.systemUTC());
  }
}
