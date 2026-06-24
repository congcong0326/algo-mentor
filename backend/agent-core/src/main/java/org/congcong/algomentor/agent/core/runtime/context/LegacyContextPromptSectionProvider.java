package org.congcong.algomentor.agent.core.runtime.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.prompt.PromptAssemblyRequest;
import org.congcong.algomentor.agent.core.prompt.PromptBudgetPolicy;
import org.congcong.algomentor.agent.core.prompt.PromptCachePolicy;
import org.congcong.algomentor.agent.core.prompt.PromptProfile;
import org.congcong.algomentor.agent.core.prompt.PromptRenderMode;
import org.congcong.algomentor.agent.core.prompt.PromptSection;
import org.congcong.algomentor.agent.core.prompt.PromptSectionProvider;
import org.congcong.algomentor.agent.core.prompt.PromptSensitivity;
import org.congcong.algomentor.agent.core.prompt.PromptSlot;
import org.congcong.algomentor.agent.core.prompt.PromptSourceRef;
import org.congcong.algomentor.agent.core.prompt.PromptTrustLevel;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.llm.core.request.LlmMessage;

final class LegacyContextPromptSectionProvider implements PromptSectionProvider {

  private static final String SYSTEM_PROMPT = "systemPrompt";
  private static final String ACTIVE_SUMMARY = "activeSummary";
  private static final String HISTORY = "history";
  private static final String CURRENT_USER_MESSAGE = "currentUserMessage";
  private static final String TEXT = "text";

  private final ContextAssemblyPolicy policy;

  LegacyContextPromptSectionProvider(ContextAssemblyPolicy policy) {
    this.policy = policy == null ? ContextAssemblyPolicy.defaultPolicy() : policy;
  }

  @Override
  public List<PromptSection> sections(PromptAssemblyRequest request, PromptProfile profile) {
    List<PromptSection> sections = new ArrayList<>();
    addSystemPrompt(request, sections);
    addActiveSummary(request, sections);
    addHistory(request, sections);
    addCurrentUserMessage(request, sections);
    return List.copyOf(sections);
  }

  private void addSystemPrompt(PromptAssemblyRequest request, List<PromptSection> sections) {
    String systemPrompt = stringVariable(request, SYSTEM_PROMPT);
    if (systemPrompt == null || systemPrompt.isBlank()) {
      return;
    }
    sections.add(new PromptSection(
        LegacyContextPromptConstants.SYSTEM_SECTION_ID,
        "System prompt",
        PromptSlot.STATIC_INSTRUCTION,
        LlmMessage.Role.SYSTEM,
        PromptTrustLevel.SYSTEM_STATIC,
        PromptSensitivity.INTERNAL_TRACE,
        0,
        false,
        policy.policyVersion(),
        PromptCachePolicy.NO_CACHE,
        PromptBudgetPolicy.FAIL_IF_OVER_BUDGET,
        PromptRenderMode.PLAIN_TEXT,
        new PromptSourceRef("legacy-context", "system-prompt", Map.of()),
        Map.of(TEXT, systemPrompt)));
  }

  private void addActiveSummary(PromptAssemblyRequest request, List<PromptSection> sections) {
    String activeSummary = stringVariable(request, ACTIVE_SUMMARY);
    if (activeSummary == null || activeSummary.isBlank()) {
      return;
    }
    sections.add(new PromptSection(
        LegacyContextPromptConstants.SUMMARY_SECTION_ID,
        "Conversation summary",
        PromptSlot.MEMORY_SUMMARY,
        LlmMessage.Role.SYSTEM,
        PromptTrustLevel.MODEL_GENERATED,
        PromptSensitivity.USER_CONTENT,
        40,
        false,
        policy.policyVersion(),
        PromptCachePolicy.NO_CACHE,
        PromptBudgetPolicy.TRUNCATE_IF_NEEDED,
        PromptRenderMode.PLAIN_TEXT,
        new PromptSourceRef("legacy-context", "active-summary", Map.of()),
        Map.of(TEXT, "Conversation summary:\n" + activeSummary)));
  }

  private void addHistory(PromptAssemblyRequest request, List<PromptSection> sections) {
    List<AgentMessage> recentMessages = recentMessages(agentMessages(request), policy.recentTurns());
    for (int index = 0; index < recentMessages.size(); index++) {
      AgentMessage message = recentMessages.get(index);
      LlmMessage.Role role = toLlmRole(message.role());
      PromptTrustLevel trustLevel = role == LlmMessage.Role.USER
          ? PromptTrustLevel.USER_INPUT
          : PromptTrustLevel.MODEL_GENERATED;
      sections.add(new PromptSection(
          LegacyContextPromptConstants.HISTORY_SECTION_PREFIX + message.sequenceNo(),
          "History message " + message.sequenceNo(),
          PromptSlot.HISTORY,
          role,
          trustLevel,
          PromptSensitivity.USER_CONTENT,
          100 + index,
          false,
          policy.policyVersion(),
          PromptCachePolicy.NO_CACHE,
          PromptBudgetPolicy.DROP_IF_NEEDED,
          PromptRenderMode.PLAIN_TEXT,
          new PromptSourceRef(
              "agent-message",
              String.valueOf(message.id()),
              Map.of("sequenceNo", message.sequenceNo())),
          Map.of(TEXT, message.content())));
    }
  }

  private void addCurrentUserMessage(PromptAssemblyRequest request, List<PromptSection> sections) {
    String currentUserMessage = stringVariable(request, CURRENT_USER_MESSAGE);
    sections.add(new PromptSection(
        LegacyContextPromptConstants.CURRENT_USER_SECTION_ID,
        "Current user message",
        PromptSlot.CURRENT_USER_MESSAGE,
        LlmMessage.Role.USER,
        PromptTrustLevel.USER_INPUT,
        PromptSensitivity.USER_CONTENT,
        10,
        true,
        policy.policyVersion(),
        PromptCachePolicy.NO_CACHE,
        PromptBudgetPolicy.TRUNCATE_IF_NEEDED,
        PromptRenderMode.PLAIN_TEXT,
        new PromptSourceRef("legacy-context", "current-user-message", Map.of()),
        Map.of(TEXT, currentUserMessage)));
  }

  private List<AgentMessage> recentMessages(List<AgentMessage> history, int recentTurns) {
    if (history.isEmpty()) {
      return List.of();
    }
    int messageLimit = recentTurns * 2;
    List<AgentMessage> sorted = history.stream()
        .sorted(Comparator.comparingLong(AgentMessage::sequenceNo))
        .toList();
    int fromIndex = Math.max(0, sorted.size() - messageLimit);
    return sorted.subList(fromIndex, sorted.size());
  }

  private List<AgentMessage> agentMessages(PromptAssemblyRequest request) {
    Object value = request.variables().get(HISTORY);
    if (!(value instanceof List<?> items)) {
      return List.of();
    }
    return items.stream()
        .filter(AgentMessage.class::isInstance)
        .map(AgentMessage.class::cast)
        .toList();
  }

  private String stringVariable(PromptAssemblyRequest request, String name) {
    Object value = request.variables().get(name);
    return value instanceof String text ? text : null;
  }

  private LlmMessage.Role toLlmRole(AgentMessage.Role role) {
    return switch (role) {
      case USER -> LlmMessage.Role.USER;
      case ASSISTANT -> LlmMessage.Role.ASSISTANT;
    };
  }
}
