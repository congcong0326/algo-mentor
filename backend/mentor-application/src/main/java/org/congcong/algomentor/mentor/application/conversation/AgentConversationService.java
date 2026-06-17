package org.congcong.algomentor.mentor.application.conversation;

import java.util.HashMap;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentRequest;

public class AgentConversationService {

  private final ConversationRepository conversationRepository;
  private final ContextAssembler contextAssembler;
  private final ContextAssemblyPolicy contextPolicy;

  public AgentConversationService(
      ConversationRepository conversationRepository,
      ContextAssembler contextAssembler
  ) {
    this(conversationRepository, contextAssembler, ContextAssemblyPolicy.defaultPolicy());
  }

  public AgentConversationService(
      ConversationRepository conversationRepository,
      ContextAssembler contextAssembler,
      ContextAssemblyPolicy contextPolicy
  ) {
    this.conversationRepository = conversationRepository;
    this.contextAssembler = contextAssembler;
    this.contextPolicy = contextPolicy == null ? ContextAssemblyPolicy.defaultPolicy() : contextPolicy;
  }

  public AgentConversationRun prepareRun(AgentConversationCommand command) {
    ConversationDraft draft = conversationRepository.createOrReuseRun(command);
    AssembledContext context = contextAssembler.assemble(
        draft.systemPrompt(),
        draft.activeSummary(),
        conversationRepository.recentMessages(draft.taskId(), contextPolicy.recentTurns() * 2),
        command.userMessage(),
        contextPolicy);

    Map<String, Object> metadata = new HashMap<>(draft.metadata());
    metadata.putAll(context.metadata());
    metadata.put("taskId", draft.taskId());
    metadata.put("turnId", draft.turnId());
    metadata.put("runDbId", draft.runId());
    metadata.put("title", "task-" + draft.taskId());

    AgentRequest request = new AgentRequest(
        draft.runUuid(),
        draft.requestId(),
        context.messages(),
        metadata);
    return new AgentConversationRun(draft.taskId(), draft.turnId(), draft.runId(), draft.runUuid(), request);
  }
}
