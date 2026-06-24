package org.congcong.algomentor.agent.core.prompt;

import java.util.Comparator;
import java.util.List;
import org.congcong.algomentor.llm.core.request.LlmMessage;

public class DefaultPromptMessageNormalizer implements PromptMessageNormalizer {

  @Override
  public List<LlmMessage> normalize(PromptProfile profile, List<RenderedPromptSection> renderedSections) {
    return renderedSections.stream()
        .filter(RenderedPromptSection::included)
        .sorted(sectionOrder(profile))
        .map(DefaultPromptMessageNormalizer::toMessage)
        .toList();
  }

  private Comparator<RenderedPromptSection> sectionOrder(PromptProfile profile) {
    return Comparator
        .comparingInt((RenderedPromptSection rendered) -> slotIndex(profile, rendered.section().slot()))
        .thenComparingInt(rendered -> rendered.section().priority())
        .thenComparing(rendered -> rendered.section().id());
  }

  private int slotIndex(PromptProfile profile, PromptSlot slot) {
    int index = profile.slotOrder().indexOf(slot);
    return index < 0 ? Integer.MAX_VALUE : index;
  }

  private static LlmMessage toMessage(RenderedPromptSection rendered) {
    String text = rendered.renderedText();
    return switch (rendered.section().targetRole()) {
      case SYSTEM -> LlmMessage.system(text);
      case USER -> LlmMessage.user(text);
      case ASSISTANT -> LlmMessage.assistant(text);
      case TOOL -> throw new PromptAssemblyException("Tool messages require provider-specific tool call ids");
    };
  }
}
