package org.congcong.algomentor.agent.core.prompt;

import java.util.Map;
import org.congcong.algomentor.llm.core.request.LlmMessage;

public record PromptSectionSnapshot(
    String id,
    String title,
    PromptSlot slot,
    LlmMessage.Role targetRole,
    PromptTrustLevel trustLevel,
    PromptSensitivity sensitivity,
    String version,
    PromptSourceRef sourceRef,
    boolean included,
    boolean truncated,
    int charCount,
    int tokenEstimate,
    String contentHash,
    Map<String, Object> redactedVariables
) {

  public PromptSectionSnapshot {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Prompt section snapshot id must not be blank");
    }
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Prompt section snapshot title must not be blank");
    }
    if (slot == null) {
      throw new IllegalArgumentException("Prompt section snapshot slot must not be null");
    }
    if (targetRole == null) {
      throw new IllegalArgumentException("Prompt section snapshot target role must not be null");
    }
    if (trustLevel == null) {
      throw new IllegalArgumentException("Prompt section snapshot trust level must not be null");
    }
    if (sensitivity == null) {
      throw new IllegalArgumentException("Prompt section snapshot sensitivity must not be null");
    }
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("Prompt section snapshot version must not be blank");
    }
    sourceRef = sourceRef == null ? PromptSourceRef.none() : sourceRef;
    if (charCount < 0) {
      throw new IllegalArgumentException("Prompt section snapshot char count must not be negative");
    }
    if (tokenEstimate < 0) {
      throw new IllegalArgumentException("Prompt section snapshot token estimate must not be negative");
    }
    if (contentHash == null || contentHash.isBlank()) {
      throw new IllegalArgumentException("Prompt section snapshot content hash must not be blank");
    }
    redactedVariables = redactedVariables == null ? Map.of() : Map.copyOf(redactedVariables);
  }
}
