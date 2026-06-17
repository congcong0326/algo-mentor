package org.congcong.algomentor.mentor.application.conversation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
      List<ConversationMessage> history,
      String currentUserMessage
  ) {
    return assemble(systemPrompt, activeSummary, history, currentUserMessage, defaultPolicy);
  }

  public AssembledContext assemble(
      String systemPrompt,
      String activeSummary,
      List<ConversationMessage> history,
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
    metadata.put("contextPolicy", effectivePolicy.policyName());
    metadata.put("contextPolicyVersion", effectivePolicy.policyVersion());
    metadata.put("tokenBudget", effectivePolicy.tokenBudget());
    metadata.put("tokenEstimate", tokenEstimate);
    return new AssembledContext(messages, metadata, tokenEstimate);
  }

  private List<ConversationMessage> recentMessages(List<ConversationMessage> history, int recentTurns) {
    if (history == null || history.isEmpty()) {
      return List.of();
    }
    int messageLimit = recentTurns * 2;
    List<ConversationMessage> sorted = history.stream()
        .sorted(Comparator.comparingLong(ConversationMessage::sequenceNo))
        .toList();
    int fromIndex = Math.max(0, sorted.size() - messageLimit);
    return sorted.subList(fromIndex, sorted.size());
  }

  private LlmMessage toLlmMessage(ConversationMessage message) {
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
