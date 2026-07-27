package org.congcong.algomentor.mentor.application.prompt;

import java.util.Map;
import org.congcong.algomentor.agent.core.prompt.PromptBudgetPolicy;
import org.congcong.algomentor.agent.core.prompt.PromptCachePolicy;
import org.congcong.algomentor.agent.core.prompt.PromptRenderMode;
import org.congcong.algomentor.agent.core.prompt.PromptSection;
import org.congcong.algomentor.agent.core.prompt.PromptSensitivity;
import org.congcong.algomentor.agent.core.prompt.PromptSlot;
import org.congcong.algomentor.agent.core.prompt.PromptSourceRef;
import org.congcong.algomentor.agent.core.prompt.PromptTrustLevel;
import org.congcong.algomentor.llm.core.request.LlmMessage;

/** 从已解析 snapshot 创建 SYSTEM_STATIC PromptSection，保留受信来源。 */
public final class ManagedSystemPromptSectionFactory {

  public static final String SOURCE_TYPE = "managed-system-prompt";
  public static final String TEXT_VARIABLE = "text";

  private ManagedSystemPromptSectionFactory() {
  }

  public static PromptSection create(
      ResolvedSystemPromptSnapshot snapshot,
      String sectionKey,
      String sectionId,
      String title,
      PromptSlot slot,
      int priority,
      PromptCachePolicy cachePolicy,
      PromptBudgetPolicy budgetPolicy,
      PromptRenderMode renderMode,
      Map<String, Object> sourceMetadata,
      String renderedText
  ) {
    String text = renderedText == null ? snapshot.requireSection(sectionKey).text() : renderedText;
    Map<String, Object> metadata = new java.util.LinkedHashMap<>();
    metadata.put("typeCode", snapshot.typeCode());
    metadata.put("sectionKey", sectionKey);
    metadata.put("contentHash", ManagedSystemPromptDefinitionRegistry.sha256(text));
    if (sourceMetadata != null) {
      metadata.putAll(sourceMetadata);
    }
    return new PromptSection(
        sectionId,
        title,
        slot,
        LlmMessage.Role.SYSTEM,
        PromptTrustLevel.SYSTEM_STATIC,
        PromptSensitivity.PUBLIC_FACT,
        priority,
        true,
        sectionKey.contains("coach-style") || sectionKey.equals(SystemPromptSectionKeys.PRACTICE_INTERACTION) ? "v2" : "v1",
        cachePolicy,
        budgetPolicy,
        renderMode,
        new PromptSourceRef(SOURCE_TYPE, sectionKey, Map.copyOf(metadata)),
        Map.of(TEXT_VARIABLE, text));
  }
}
