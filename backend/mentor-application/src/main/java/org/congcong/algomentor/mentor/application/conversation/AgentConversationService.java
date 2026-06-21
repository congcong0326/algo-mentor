package org.congcong.algomentor.mentor.application.conversation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.runtime.context.AssembledContext;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssemblyPolicy;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;

public class AgentConversationService {

  private static final String DEFAULT_MENTOR_SYSTEM_PROMPT =
      "You are an algorithm learning mentor. Explain clearly, ask guiding questions when useful, and prefer Java examples.";

  private final AgentConversationRepository conversationRepository;
  private final ContextAssembler contextAssembler;
  private final ContextAssemblyPolicy contextPolicy;

  public AgentConversationService(
      AgentConversationRepository conversationRepository,
      ContextAssembler contextAssembler
  ) {
    this(conversationRepository, contextAssembler, ContextAssemblyPolicy.defaultPolicy());
  }

  public AgentConversationService(
      AgentConversationRepository conversationRepository,
      ContextAssembler contextAssembler,
      ContextAssemblyPolicy contextPolicy
  ) {
    this.conversationRepository = conversationRepository;
    this.contextAssembler = contextAssembler;
    this.contextPolicy = contextPolicy == null ? ContextAssemblyPolicy.defaultPolicy() : contextPolicy;
  }

  public AgentConversationRun prepareRun(AgentConversationCommand command) {
    PreparedAgentRun draft = conversationRepository.createOrReuseRun(toPreparationRequest(command));
    return toConversationRun(draft, command.userMessage());
  }

  public Optional<AgentConversationRun> findRunByIdempotencyKey(String idempotencyKey, String userMessage) {
    return conversationRepository.findRunByIdempotencyKey(idempotencyKey)
        .map(draft -> toConversationRun(draft, userMessage));
  }

  private AgentConversationRun toConversationRun(PreparedAgentRun draft, String userMessage) {
    AssembledContext context = contextAssembler.assemble(
        draft.systemPrompt(),
        draft.activeSummary(),
        conversationRepository.recentMessages(draft.taskId(), contextPolicy.recentTurns() * 2),
        userMessage,
        contextPolicy);

    Map<String, Object> metadata = new HashMap<>(draft.metadata());
    metadata.putAll(context.metadata());
    metadata.put(AgentRuntimeMetadataKeys.TASK_ID, draft.taskId());
    metadata.put(AgentRuntimeMetadataKeys.TURN_ID, draft.turnId());
    metadata.put(AgentRuntimeMetadataKeys.RUN_DB_ID, draft.runId());
    metadata.put(AgentRuntimeMetadataKeys.AGENT_RUN_ID, draft.runUuid());
    metadata.put(AgentRuntimeMetadataKeys.TITLE, "task-" + draft.taskId());

    AgentRequest request = new AgentRequest(
        draft.runUuid(),
        draft.requestId(),
        context.messages(),
        metadata);
    return new AgentConversationRun(draft.taskId(), draft.turnId(), draft.runId(), draft.runUuid(), request);
  }

  private AgentRunPreparationRequest toPreparationRequest(AgentConversationCommand command) {
    return new AgentRunPreparationRequest(
        command.taskId(),
        command.userId(),
        command.userMessage(),
        command.idempotencyKey(),
        DEFAULT_MENTOR_SYSTEM_PROMPT,
        Map.of("triggerType", "user_request"));
  }
}
