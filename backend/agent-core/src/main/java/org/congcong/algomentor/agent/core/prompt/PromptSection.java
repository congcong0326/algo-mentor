package org.congcong.algomentor.agent.core.prompt;

import java.util.Map;
import org.congcong.algomentor.llm.core.request.LlmMessage;

public record PromptSection(
    String id,
    String title,
    PromptSlot slot,
    LlmMessage.Role targetRole,
    PromptTrustLevel trustLevel,
    PromptSensitivity sensitivity,
    int priority,
    boolean required,
    String version,
    PromptCachePolicy cachePolicy,
    PromptBudgetPolicy budgetPolicy,
    PromptRenderMode renderMode,
    PromptSourceRef sourceRef,
    Map<String, Object> variables
) {

  public PromptSection {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Prompt section id must not be blank");
    }
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Prompt section title must not be blank");
    }
    if (slot == null) {
      throw new IllegalArgumentException("Prompt section slot must not be null");
    }
    if (targetRole == null) {
      throw new IllegalArgumentException("Prompt section target role must not be null");
    }
    if (trustLevel == null) {
      throw new IllegalArgumentException("Prompt section trust level must not be null");
    }
    if (sensitivity == null) {
      throw new IllegalArgumentException("Prompt section sensitivity must not be null");
    }
    if (sensitivity == PromptSensitivity.SECRET) {
      throw new IllegalArgumentException("Prompt section secret content must not enter prompt assembly");
    }
    if (priority < 0) {
      throw new IllegalArgumentException("Prompt section priority must not be negative");
    }
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("Prompt section version must not be blank");
    }
    if (cachePolicy == null) {
      throw new IllegalArgumentException("Prompt section cache policy must not be null");
    }
    if (budgetPolicy == null) {
      throw new IllegalArgumentException("Prompt section budget policy must not be null");
    }
    if (renderMode == null) {
      throw new IllegalArgumentException("Prompt section render mode must not be null");
    }
    sourceRef = sourceRef == null ? PromptSourceRef.none() : sourceRef;
    variables = variables == null ? Map.of() : Map.copyOf(variables);
    validateTrustBoundary(slot, targetRole, trustLevel);
  }

  private static void validateTrustBoundary(
      PromptSlot slot,
      LlmMessage.Role targetRole,
      PromptTrustLevel trustLevel
  ) {
    if (slot == PromptSlot.STATIC_INSTRUCTION && trustLevel != PromptTrustLevel.SYSTEM_STATIC) {
      throw new IllegalArgumentException("Static instruction sections must be system-static");
    }
    if (slot == PromptSlot.CURRENT_USER_MESSAGE) {
      if (targetRole != LlmMessage.Role.USER || trustLevel != PromptTrustLevel.USER_INPUT) {
        throw new IllegalArgumentException("Current user message sections must remain user input");
      }
    }
    if (slot == PromptSlot.HISTORY && trustLevel == PromptTrustLevel.SYSTEM_STATIC) {
      throw new IllegalArgumentException("History sections must not be system-static");
    }
    if (slot == PromptSlot.TOOL_RESULT && trustLevel != PromptTrustLevel.TOOL_OUTPUT) {
      throw new IllegalArgumentException("Tool result sections must keep tool-output trust");
    }
    if (targetRole == LlmMessage.Role.SYSTEM && trustLevel == PromptTrustLevel.USER_INPUT) {
      throw new IllegalArgumentException("User input must not be rendered as system prompt");
    }
    if (targetRole == LlmMessage.Role.SYSTEM && trustLevel == PromptTrustLevel.TOOL_OUTPUT) {
      throw new IllegalArgumentException("Tool output must not be rendered as system prompt");
    }
    if (targetRole == LlmMessage.Role.SYSTEM
        && trustLevel == PromptTrustLevel.MODEL_GENERATED
        && slot != PromptSlot.MEMORY_SUMMARY) {
      throw new IllegalArgumentException("Model-generated system content is only allowed as memory summary");
    }
  }
}
