package org.congcong.algomentor.mentor.api.autoconfigure;

import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.persistence.postgres.config.AgentPostgresPersistenceConfiguration;
import org.congcong.algomentor.api.controller.AgentConversationController;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
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
  @ConditionalOnBean({AgentConversationService.class, AgentLoopRunner.class, LlmStreamSseMapper.class})
  @ConditionalOnMissingBean
  public AgentConversationController agentConversationController(
      AgentConversationService conversationService,
      AgentLoopRunner agentLoopRunner,
      LlmStreamSseMapper sseMapper
  ) {
    return new AgentConversationController(conversationService, agentLoopRunner, sseMapper);
  }
}
