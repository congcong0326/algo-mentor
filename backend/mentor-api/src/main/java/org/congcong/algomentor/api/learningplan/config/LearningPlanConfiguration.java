package org.congcong.algomentor.api.learningplan.config;

import java.time.Clock;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.api.learningplan.repository.UnavailableLearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanAgentService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionApplyService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionValidator;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalPromptBuilder;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionStreamService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanExtensionProposalStreamService;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftPromptBuilder;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStreamService;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

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
  public LearningPlanDraftPromptBuilder learningPlanDraftPromptBuilder() {
    return new LearningPlanDraftPromptBuilder();
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanDraftStreamService learningPlanDraftStreamService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanDraftValidator validator,
      AgentLoopRunner agentLoopRunner,
      LearningPlanDraftPromptBuilder promptBuilder,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      Clock learningPlanClock) {
    return new LearningPlanDraftStreamService(
        draftRepository,
        validator,
        agentLoopRunner,
        promptBuilder,
        objectMapper,
        problemCatalog,
        learningPlanClock);
  }

  @Bean
  @ConditionalOnBean(LearningPlanProposalRepository.class)
  @ConditionalOnMissingBean
  public LearningPlanProposalGroupService learningPlanProposalGroupService(
      LearningPlanProposalRepository proposalRepository,
      Clock learningPlanClock) {
    return new LearningPlanProposalGroupService(proposalRepository, learningPlanClock);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanExtensionValidator learningPlanExtensionValidator(LearningPlanProblemCatalog problemCatalog) {
    return new LearningPlanExtensionValidator(problemCatalog);
  }

  @Bean
  @ConditionalOnMissingBean
  public LearningPlanProposalPromptBuilder learningPlanProposalPromptBuilder(ObjectMapper objectMapper) {
    return new LearningPlanProposalPromptBuilder(objectMapper);
  }

  @Bean
  @ConditionalOnBean(LearningPlanProposalRepository.class)
  @ConditionalOnMissingBean
  public LearningPlanDraftRevisionStreamService learningPlanDraftRevisionStreamService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanProposalRepository proposalRepository,
      LearningPlanProposalGroupService groupService,
      LearningPlanDraftValidator validator,
      AgentLoopRunner agentLoopRunner,
      LearningPlanDraftPromptBuilder promptBuilder,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      TransactionOperations transactionOperations,
      Clock learningPlanClock) {
    return new LearningPlanDraftRevisionStreamService(
        draftRepository,
        proposalRepository,
        groupService,
        validator,
        agentLoopRunner,
        promptBuilder,
        objectMapper,
        problemCatalog,
        transactionOperations,
        learningPlanClock);
  }

  @Bean
  @ConditionalOnBean(LearningPlanProposalRepository.class)
  @ConditionalOnMissingBean
  public LearningPlanExtensionProposalStreamService learningPlanExtensionProposalStreamService(
      LearningPlanRepository planRepository,
      LearningPlanProposalRepository proposalRepository,
      LearningPlanProposalGroupService groupService,
      PracticeSessionRepository practiceSessionRepository,
      LearningPlanExtensionValidator validator,
      AgentLoopRunner agentLoopRunner,
      LearningPlanProposalPromptBuilder promptBuilder,
      ObjectMapper objectMapper,
      TransactionOperations transactionOperations,
      Clock learningPlanClock) {
    return new LearningPlanExtensionProposalStreamService(
        planRepository,
        proposalRepository,
        groupService,
        practiceSessionRepository,
        validator,
        agentLoopRunner,
        promptBuilder,
        objectMapper,
        transactionOperations,
        learningPlanClock);
  }

  @Bean
  @ConditionalOnBean(LearningPlanProposalRepository.class)
  @ConditionalOnMissingBean
  public LearningPlanExtensionApplyService learningPlanExtensionApplyService(
      LearningPlanProposalRepository proposalRepository,
      LearningPlanRepository planRepository,
      PracticeSessionRepository practiceSessionRepository,
      LearningPlanExtensionValidator validator,
      Clock learningPlanClock) {
    return new LearningPlanExtensionApplyService(
        proposalRepository,
        planRepository,
        practiceSessionRepository,
        validator,
        learningPlanClock);
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
