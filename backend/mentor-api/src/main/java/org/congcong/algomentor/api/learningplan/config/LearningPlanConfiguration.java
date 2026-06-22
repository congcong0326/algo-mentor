package org.congcong.algomentor.api.learningplan.config;

import java.time.Clock;
import org.congcong.algomentor.api.learningplan.repository.UnavailableLearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanAgentService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LearningPlanConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public Clock learningPlanClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanDraftValidator learningPlanDraftValidator() {
    return new LearningPlanDraftValidator();
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanAgentService learningPlanAgentService(LearningPlanProblemCatalog problemCatalog) {
    return new LearningPlanAgentService(problemCatalog);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanDraftService learningPlanDraftService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanRepository planRepository,
      LearningPlanAgentService agentService,
      LearningPlanDraftValidator validator,
      Clock learningPlanClock) {
    return new LearningPlanDraftService(draftRepository, planRepository, agentService, validator, learningPlanClock);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanService learningPlanService(LearningPlanRepository planRepository) {
    return new LearningPlanService(planRepository);
  }

  @Bean
  @ConditionalOnMissingBean({LearningPlanDraftRepository.class, LearningPlanRepository.class})
  public UnavailableLearningPlanRepository unavailableLearningPlanRepository() {
    return new UnavailableLearningPlanRepository();
  }
}
