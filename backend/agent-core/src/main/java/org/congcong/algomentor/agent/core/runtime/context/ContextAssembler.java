package org.congcong.algomentor.agent.core.runtime.context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.prompt.AgentPromptMetadataKeys;
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
    Map<String, Object> metadata = new LinkedHashMap<>();
    copyPromptAuditMetadata(assembly.metadata(), metadata);
    metadata.put(AgentRuntimeMetadataKeys.CONTEXT_POLICY, effectivePolicy.policyName());
    metadata.put(AgentRuntimeMetadataKeys.CONTEXT_POLICY_VERSION, effectivePolicy.policyVersion());
    metadata.put(AgentRuntimeMetadataKeys.TOKEN_BUDGET, effectivePolicy.tokenBudget());
    metadata.put(AgentRuntimeMetadataKeys.TOKEN_ESTIMATE, legacyTokenEstimate);
    return new AssembledContext(
        assembly.canonicalMessages(),
        Map.copyOf(metadata),
        legacyTokenEstimate);
  }

  /**
   * 保留 Prompt Assembly 已脱敏的审计决策，供最终请求快照说明裁剪与丢弃原因。
   *
   * <p>不能复制任意组装 metadata，避免以后新增的运行期输入意外进入 Agent 请求 metadata。</p>
   */
  private void copyPromptAuditMetadata(Map<String, Object> source, Map<String, Object> target) {
    List<String> keys = List.of(
        AgentPromptMetadataKeys.PROMPT_PROFILE,
        AgentPromptMetadataKeys.PROMPT_PROFILE_VERSION,
        AgentPromptMetadataKeys.PROMPT_SECTION_VERSIONS,
        AgentPromptMetadataKeys.PROMPT_POLICY,
        AgentPromptMetadataKeys.PROMPT_POLICY_VERSION,
        AgentPromptMetadataKeys.PROMPT_TOKEN_BUDGET,
        AgentPromptMetadataKeys.PROMPT_TOKEN_ESTIMATE,
        AgentPromptMetadataKeys.PROMPT_TRUNCATED_SECTIONS,
        AgentPromptMetadataKeys.PROMPT_SECTION_ACTIONS,
        AgentPromptMetadataKeys.PROMPT_CONTENT_HASHES,
        AgentPromptMetadataKeys.PROMPT_SECTION_SNAPSHOTS,
        AgentRuntimeMetadataKeys.TRUNCATED_SECTION_IDS,
        AgentRuntimeMetadataKeys.DROPPED_SECTION_IDS);
    for (String key : keys) {
      Object value = source.get(key);
      if (value != null) {
        target.put(key, value);
      }
    }
  }

  private int estimateTokens(List<LlmMessage> messages) {
    int chars = messages.stream()
        .mapToInt(message -> message.text().length())
        .sum();
    return Math.max(1, chars / 4);
  }
}
