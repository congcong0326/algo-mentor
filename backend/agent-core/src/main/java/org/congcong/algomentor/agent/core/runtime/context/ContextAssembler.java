package org.congcong.algomentor.agent.core.runtime.context;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.prompt.DefaultPromptAssembler;
import org.congcong.algomentor.agent.core.prompt.PromptAssembly;
import org.congcong.algomentor.agent.core.prompt.PromptAssemblyRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.request.LlmMessage;

public class ContextAssembler {

  private static final String SYSTEM_PROMPT = "systemPrompt";
  private static final String ACTIVE_SUMMARY = "activeSummary";
  private static final String HISTORY = "history";
  private static final String CURRENT_USER_MESSAGE = "currentUserMessage";

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
    PromptAssembly assembly = new DefaultPromptAssembler(
        new LegacyContextPromptProfileResolver(effectivePolicy),
        List.of(new LegacyContextPromptSectionProvider(effectivePolicy)))
        .assemble(new PromptAssemblyRequest(
            LegacyContextPromptConstants.SCENARIO,
            LegacyContextPromptConstants.PROFILE_ID,
            effectivePolicy.tokenBudget(),
            Map.of(
                SYSTEM_PROMPT, systemPrompt == null ? "" : systemPrompt,
                ACTIVE_SUMMARY, activeSummary == null ? "" : activeSummary,
                HISTORY, history == null ? List.of() : List.copyOf(history),
                CURRENT_USER_MESSAGE, currentUserMessage),
            Map.of()));

    int legacyTokenEstimate = estimateTokens(assembly.canonicalMessages());
    return new AssembledContext(
        assembly.canonicalMessages(),
        Map.of(
            AgentRuntimeMetadataKeys.CONTEXT_POLICY, effectivePolicy.policyName(),
            AgentRuntimeMetadataKeys.CONTEXT_POLICY_VERSION, effectivePolicy.policyVersion(),
            AgentRuntimeMetadataKeys.TOKEN_BUDGET, effectivePolicy.tokenBudget(),
            AgentRuntimeMetadataKeys.TOKEN_ESTIMATE, legacyTokenEstimate),
        legacyTokenEstimate);
  }

  private int estimateTokens(List<LlmMessage> messages) {
    int chars = messages.stream()
        .mapToInt(message -> message.text().length())
        .sum();
    return Math.max(1, chars / 4);
  }
}
