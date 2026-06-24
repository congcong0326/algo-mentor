package org.congcong.algomentor.agent.core.prompt;

import java.util.List;
import java.util.Set;

public record PromptProfile(
    String id,
    String version,
    String policyName,
    String policyVersion,
    int tokenBudget,
    Set<String> requiredSectionIds,
    List<PromptSlot> slotOrder
) {

  public PromptProfile {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Prompt profile id must not be blank");
    }
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("Prompt profile version must not be blank");
    }
    if (policyName == null || policyName.isBlank()) {
      throw new IllegalArgumentException("Prompt profile policy name must not be blank");
    }
    if (policyVersion == null || policyVersion.isBlank()) {
      throw new IllegalArgumentException("Prompt profile policy version must not be blank");
    }
    if (tokenBudget < 1) {
      throw new IllegalArgumentException("Prompt profile token budget must be positive");
    }
    requiredSectionIds = requiredSectionIds == null ? Set.of() : Set.copyOf(requiredSectionIds);
    slotOrder = slotOrder == null || slotOrder.isEmpty()
        ? PromptSlot.canonicalOrder()
        : List.copyOf(slotOrder);
  }
}
