package org.congcong.algomentor.agent.core.runtime.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.request.LlmMessage;

public class ContextAssembler {

  private final ContextAssemblyPolicy defaultPolicy;

  public ContextAssembler() {
    this(ContextAssemblyPolicy.defaultPolicy());
  }

  public ContextAssembler(ContextAssemblyPolicy defaultPolicy) {
    this.defaultPolicy = defaultPolicy;
  }

  public AssembledContext assemble(
      String systemPrompt,
      String activeSummary,
      List<AgentMessage> history,
      String currentUserMessage
  ) {
    return assemble(systemPrompt, activeSummary, history, currentUserMessage, defaultPolicy);
  }

  public AssembledContext assemble(
      String systemPrompt,
      String activeSummary,
      List<AgentMessage> history,
      String currentUserMessage,
      ContextAssemblyPolicy policy
  ) {
    if (currentUserMessage == null || currentUserMessage.isBlank()) {
      throw new IllegalArgumentException("Current user message must not be blank");
    }
    ContextAssemblyPolicy effectivePolicy = policy == null ? defaultPolicy : policy;
    List<LlmMessage> messages = new ArrayList<>();
    if (systemPrompt != null && !systemPrompt.isBlank()) {
      messages.add(LlmMessage.system(systemPrompt));
    }
    if (activeSummary != null && !activeSummary.isBlank()) {
      messages.add(LlmMessage.system("Conversation summary:\n" + activeSummary));
    }
    recentMessages(history, effectivePolicy.recentTurns())
        .forEach(message -> messages.add(toLlmMessage(message)));
    messages.add(LlmMessage.user(currentUserMessage));

    int tokenEstimate = estimateTokens(messages);
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(AgentRuntimeMetadataKeys.CONTEXT_POLICY, effectivePolicy.policyName());
    metadata.put(AgentRuntimeMetadataKeys.CONTEXT_POLICY_VERSION, effectivePolicy.policyVersion());
    metadata.put(AgentRuntimeMetadataKeys.TOKEN_BUDGET, effectivePolicy.tokenBudget());
    metadata.put(AgentRuntimeMetadataKeys.TOKEN_ESTIMATE, tokenEstimate);
    return new AssembledContext(messages, metadata, tokenEstimate);
  }

  private List<AgentMessage> recentMessages(List<AgentMessage> history, int recentTurns) {
    if (history == null || history.isEmpty()) {
      return List.of();
    }
    int messageLimit = recentTurns * 2;
    List<AgentMessage> sorted = history.stream()
        .sorted(Comparator.comparingLong(AgentMessage::sequenceNo))
        .toList();
    int fromIndex = Math.max(0, sorted.size() - messageLimit);
    return sorted.subList(fromIndex, sorted.size());
  }

  private LlmMessage toLlmMessage(AgentMessage message) {
    return switch (message.role()) {
      case USER -> LlmMessage.user(message.content());
      case ASSISTANT -> LlmMessage.assistant(message.content());
    };
  }

  private int estimateTokens(List<LlmMessage> messages) {
    int chars = messages.stream()
        .mapToInt(message -> message.text().length())
        .sum();
    return Math.max(1, chars / 4);
  }
}
