package org.congcong.algomentor.mentor.api.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.ai.governance.completion.AiCompletionGateway;
import org.congcong.algomentor.api.config.PracticeCodeReviewProperties;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentTool;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewCommitService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewMetrics;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewPermissionHook;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewPromptBuilder;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewStructuredOutputMapper;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTagCatalog;
import org.congcong.algomentor.mentor.application.review.card.PracticeCodeReviewObserver;
import org.congcong.algomentor.queue.publisher.QueuePublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Practice Code Review 的强依赖装配边界。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = PracticeCodeReviewProperties.PREFIX,
    name = PracticeCodeReviewProperties.ENABLED,
    havingValue = "true")
public class PracticeCodeReviewConfiguration {

  @Bean
  public PracticeCodeReviewPromptBuilder practiceCodeReviewPromptBuilder() {
    return new PracticeCodeReviewPromptBuilder();
  }

  @Bean
  public PracticeCodeReviewStructuredOutputMapper practiceCodeReviewStructuredOutputMapper() {
    return new PracticeCodeReviewStructuredOutputMapper();
  }

  @Bean
  public PracticeCodeReviewCommitService practiceCodeReviewCommitService(
      PracticeCodeReviewRepository reviewRepository,
      QueuePublisher queuePublisher) {
    return new PracticeCodeReviewCommitService(reviewRepository, queuePublisher);
  }

  @Bean
  public PracticeCodeReviewService practiceCodeReviewService(
      PracticeCodeReviewRepository reviewRepository,
      PracticeCodeReviewCommitService commitService,
      AiCompletionGateway completionGateway,
      PracticeCodeReviewPromptBuilder promptBuilder,
      PracticeCodeReviewStructuredOutputMapper outputMapper,
      ObjectProvider<PracticeCodeReviewMetrics> metrics,
      ObjectProvider<PracticeCodeReviewObserver> observer) {
    return new PracticeCodeReviewService(
        reviewRepository,
        commitService,
        completionGateway,
        promptBuilder,
        outputMapper,
        metrics.getIfAvailable(() -> PracticeCodeReviewMetrics.NOOP),
        observer.getIfAvailable(() -> PracticeCodeReviewObserver.NOOP));
  }

  @Bean
  public PracticeCodeReviewAgentTool practiceCodeReviewAgentTool(
      PracticeSessionRepository practiceSessionRepository,
      AgentTurnMessageLookupRepository turnMessageLookupRepository,
      PracticeCodeReviewService reviewService,
      ObjectMapper objectMapper,
      TrustedProblemTagCatalog trustedProblemTagCatalog) {
    return new PracticeCodeReviewAgentTool(
        practiceSessionRepository,
        turnMessageLookupRepository,
        reviewService,
        objectMapper,
        trustedProblemTagCatalog);
  }

  @Bean
  public PracticeCodeReviewPermissionHook practiceCodeReviewPermissionHook(
      PracticeSessionRepository practiceSessionRepository,
      AgentTurnMessageLookupRepository turnMessageLookupRepository) {
    return new PracticeCodeReviewPermissionHook(practiceSessionRepository, turnMessageLookupRepository);
  }
}
