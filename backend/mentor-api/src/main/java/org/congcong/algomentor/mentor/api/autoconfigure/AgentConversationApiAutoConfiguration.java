package org.congcong.algomentor.mentor.api.autoconfigure;

import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.persistence.postgres.config.AgentPostgresPersistenceConfiguration;
import org.congcong.algomentor.api.controller.AgentConversationController;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunCoordinator;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AgentPostgresPersistenceConfiguration.class)
public class AgentConversationApiAutoConfiguration {

  @Bean
  @ConditionalOnBean({AgentConversationRepository.class, ContextAssembler.class})
  @ConditionalOnMissingBean
  public AgentConversationService agentConversationService(
      AgentConversationRepository conversationRepository,
      ContextAssembler contextAssembler
  ) {
    return new AgentConversationService(conversationRepository, contextAssembler);
  }

  @Bean
  @ConditionalOnBean({
      AgentConversationService.class,
      AgentLoopRunner.class,
      AgentRunLockManager.class,
      AgentRunLockOwnerProvider.class
  })
  @ConditionalOnMissingBean
  public AgentConversationRunCoordinator agentConversationRunCoordinator(
      AgentConversationService conversationService,
      AgentLoopRunner agentLoopRunner,
      AgentRunLockManager lockManager,
      AgentRunLockOwnerProvider lockOwnerProvider
  ) {
    return new AgentConversationRunCoordinator(
        conversationService,
        agentLoopRunner,
        lockManager,
        lockOwnerProvider);
  }

  @Bean
  @ConditionalOnBean({AgentConversationRunCoordinator.class, LlmStreamSseMapper.class})
  @ConditionalOnMissingBean
  public AgentConversationController agentConversationController(
      AgentConversationRunCoordinator runCoordinator,
      LlmStreamSseMapper sseMapper
  ) {
    return new AgentConversationController(runCoordinator, sseMapper);
  }
}
