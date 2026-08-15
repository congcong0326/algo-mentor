package org.congcong.algomentor.mentor.api.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.api.config.PracticeCodeReviewProperties;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentTool;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentDefinition;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemCatalog;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewCommitService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewMetrics;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewPermissionHook;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewPromptBuilder;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewHistoryRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewStructuredOutputMapper;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTagCatalog;
import org.congcong.algomentor.mentor.application.review.card.PracticeCodeReviewObserver;
import org.congcong.algomentor.queue.publisher.QueuePublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Practice Code Review 的强依赖装配边界。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = PracticeCodeReviewProperties.PREFIX,
    name = PracticeCodeReviewProperties.ENABLED,
    havingValue = "true")
public class PracticeCodeReviewConfiguration {

  @Bean
  public PracticeCodeReviewPromptBuilder practiceCodeReviewPromptBuilder(
      ObjectProvider<ManagedSystemPromptResolver> systemPromptResolver
  ) {
    return new PracticeCodeReviewPromptBuilder(systemPromptResolver.getIfAvailable(ManagedSystemPrompts::defaultResolver));
  }

  @Bean
  public PracticeCodeReviewStructuredOutputMapper practiceCodeReviewStructuredOutputMapper() {
    return new PracticeCodeReviewStructuredOutputMapper();
  }

  @Bean
  public PracticeCodeReviewAgentDefinition practiceCodeReviewAgentDefinition(
      PracticeCodeReviewPromptBuilder promptBuilder
  ) {
    return new PracticeCodeReviewAgentDefinition(promptBuilder);
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
      @Lazy AgentRuntime agentRuntime,
      PracticeCodeReviewStructuredOutputMapper outputMapper,
      ObjectProvider<PracticeCodeReviewHistoryRepository> historyRepository,
      ObjectProvider<PracticeCodeReviewMetrics> metrics,
      ObjectProvider<PracticeCodeReviewObserver> observer) {
    return new PracticeCodeReviewService(
        reviewRepository,
        commitService,
        agentRuntime,
        outputMapper,
        historyRepository.getIfAvailable(PracticeCodeReviewHistoryRepository::empty),
        metrics.getIfAvailable(() -> PracticeCodeReviewMetrics.NOOP),
        observer.getIfAvailable(() -> PracticeCodeReviewObserver.NOOP));
  }

  @Bean
  public PracticeCodeReviewAgentTool practiceCodeReviewAgentTool(
      PracticeSessionRepository practiceSessionRepository,
      AgentTurnMessageLookupRepository turnMessageLookupRepository,
      PracticeCodeReviewService reviewService,
      ObjectMapper objectMapper,
      TrustedProblemTagCatalog trustedProblemTagCatalog,
      PracticeChatProblemCatalog practiceChatProblemCatalog) {
    return new PracticeCodeReviewAgentTool(
        practiceSessionRepository,
        turnMessageLookupRepository,
        reviewService,
        objectMapper,
        trustedProblemTagCatalog,
        practiceChatProblemCatalog);
  }

  @Bean
  public PracticeCodeReviewPermissionHook practiceCodeReviewPermissionHook(
      PracticeSessionRepository practiceSessionRepository,
      AgentTurnMessageLookupRepository turnMessageLookupRepository) {
    return new PracticeCodeReviewPermissionHook(practiceSessionRepository, turnMessageLookupRepository);
  }
}
